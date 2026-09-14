package kr.co.mcmp.softwarecatalog.kubernetes.service;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Operator-authorized, idempotent preparation; every target comes from Tumblebug, never a client CSP ID. */
@Service @RequiredArgsConstructor
public class IbmIngressAutomationService {
    static final String LOCK = "am-ingress-automation-lock";
    static final String INTENT = "am-ibm-proxy-intent";
    private final CbtumblebugRestApi tumblebug;
    private final IbmIngressAutomationProperties properties;
    private final IbmCloudIngressApi cloud;
    private final IbmCertificateManager certificates;
    private final IbmKubernetesIngressSetup kubernetesSetup;

    private record Target(K8sClusterDto cluster, IbmIngressAutomationProperties.Profile profile) { }
    private Target target(String ns, String name) {
        var cluster = tumblebug.getK8sClusterByName(ns, name);
        if (!IbmIngressSupport.isIbm(cluster)) throw new IllegalArgumentException("Ingress automation requires an IBM cluster.");
        return new Target(cluster, properties.profile(ns, cluster.getConnectionName()));
    }

    /** No persistent writes: warn about preparable states, fail on invalid/unauthorized states. */
    public List<String> check(KubernetesClient client, String ns, String name, String workload,
                              DeploymentConfigDTO config) {
        if (!config.isIngressEnabled() || !IbmIngressSupport.managed(config.getIngressClass())) return List.of();
        Target target = target(ns, name);
        if (!config.isTlsEnabled()) return kubernetesSetup.check(client, ns, target.cluster, config);
        var warnings = new ArrayList<String>();
        certificatePlan(client, target.profile, workload, config, warnings);
        try { warnings.addAll(IbmIngressSupport.verify(client, https(config))); }
        catch (IbmIngressSupport.MissingProxyException missing) {
            requireProxyProfile(target.profile);
            IngressAutomationPermissions.lock(client);
            var session = cloud.login(target.profile);
            var cluster = cloud.cluster(session, target.cluster.getCspResourceId());
            verifyRegion(cluster.path("region").asText(), target.profile);
            if (!proxyEnabled(client, session, target.cluster.getCspResourceId())) {
                cloud.trustedSubnets(session, cluster, managedHostnames(client), true);
                if (client.configMaps().inNamespace("kube-system").withName(INTENT).get() != null)
                    throw new IllegalArgumentException("An earlier IBM PROXY update has an unresolved outcome. Ask an operator to inspect am-ibm-proxy-intent before retrying.");
                warnings.add("Deploy will enable PROXY protocol on the selected cluster's shared IBM LBs. Existing applications may briefly lose connectivity while the LBs are replaced.");
            } else warnings.add("IBM PROXY protocol is configured; Deploy will wait for the managed LBs and controllers to become ready.");
        }
        return warnings;
    }

    public DeploymentConfigDTO prepare(KubernetesClient client, String ns, String name, String workload,
                                        DeploymentConfigDTO config, Consumer<String> progress) {
        if (!config.isIngressEnabled() || !IbmIngressSupport.managed(config.getIngressClass())) return config;
        Target target = target(ns, name);
        if (!config.isTlsEnabled()) {
            var resolved = IbmIngressTlsResolver.resolve(client, workload, config);
            KubernetesIngressRouteValidator.assertAvailable(client, resolved);
            var plan = kubernetesSetup.plan(client, ns, target.cluster, resolved);
            if (plan.ready()) return resolved;
            ConfigMap lock = acquire(client, target.cluster.getCspResourceId());
            try {
                KubernetesIngressRouteValidator.assertAvailable(client, resolved);
                kubernetesSetup.prepare(client, ns, target.cluster, resolved, progress);
                IbmIngressSupport.verify(client, resolved);
                return resolved;
            } finally { release(client, lock); }
        }
        // Ready installations need no automation profile, writes or lock permission.
        try {
            var resolved = IbmIngressTlsResolver.resolve(client, workload, config);
            IbmIngressSupport.verify(client, resolved);
            return resolved;
        } catch (IbmIngressTlsResolver.UnregisteredHostException | IbmIngressSupport.MissingProxyException ignored) { }
        catch (IllegalArgumentException e) {
            // A missing IBM default certificate must not block an independently authorized custom certificate.
            if (target.profile == null || !target.profile.allowsHost(config.getIngressHost())
                    || config.getIngressHost().endsWith(".containers.appdomain.cloud")
                    || IbmIngressTlsResolver.selectBinding(IbmIngressTlsResolver.bindings(client,workload),config.getIngressHost()) != null) throw e;
        }
        KubernetesIngressRouteValidator.assertAvailable(client, config);
        check(client, ns, name, workload, config);
        ConfigMap lock = acquire(client, target.cluster.getCspResourceId());
        try {
            // Recheck all immutable inputs, domain/route policy and prerequisites before changing shared infrastructure.
            KubernetesIngressRouteValidator.assertAvailable(client, config);
            check(client, ns, name, workload, config);
            var plan = certificatePlan(client, target.profile, workload, config, new ArrayList<>());
            if (plan != null) certificates.prepare(client, ns, name, workload, config.getIngressHost(), plan, progress);
            var resolved = IbmIngressTlsResolver.resolve(client, workload, config);
            try { IbmIngressSupport.verify(client, resolved); }
            catch (IbmIngressSupport.MissingProxyException missing) {
                enableProxy(client, target, resolved, progress);
            }
            // Fail closed: no application Ingress is deployed unless certificate and actual CIDR prerequisites are ready.
            IbmIngressSupport.verify(client, resolved);
            progress.accept("Ingress and HTTPS certificate are ready.");
            return resolved;
        } finally {
            release(client, lock);
        }
    }

    private void release(KubernetesClient client, ConfigMap lock) {
        var current = client.configMaps().inNamespace("kube-system").withName(LOCK).get();
        if (current != null && Objects.equals(current.getData().get("holder"), lock.getData().get("holder")))
            client.configMaps().inNamespace("kube-system").resource(current).lockResourceVersion(current.getMetadata().getResourceVersion()).delete();
    }

    private IbmCertificateManager.Plan certificatePlan(KubernetesClient client, IbmIngressAutomationProperties.Profile profile,
                                                        String workload, DeploymentConfigDTO config, List<String> warnings) {
        // Explicit operator bindings always win. Never replace a broken/expired manually managed certificate.
        var bindings = IbmIngressTlsResolver.bindings(client, workload);
        if (IbmIngressTlsResolver.selectBinding(bindings, config.getIngressHost()) == null && profile != null
                && profile.allowsHost(config.getIngressHost()) && !config.getIngressHost().endsWith(".containers.appdomain.cloud")) {
            var plan = certificates.plan(client, profile, config.getIngressHost());
            certificates.checkWorkloadPermissions(client, workload, plan);
            warnings.add("Deploy will prepare a certificate for this authorized custom domain. DNS validation may take several minutes; the domain must also resolve to the IBM Ingress endpoint.");
            if (plan.install) warnings.add("Deploy will install cert-manager once in the selected cluster.");
            return plan;
        }
            IbmIngressTlsResolver.resolve(client, workload, config);
        return null;
    }

    private void enableProxy(KubernetesClient client, Target target, DeploymentConfigDTO config, Consumer<String> progress) {
        requireProxyProfile(target.profile);
        var session = cloud.login(target.profile);
        String id = target.cluster.getCspResourceId();
        var cluster = cloud.cluster(session, id);
        verifyRegion(cluster.path("region").asText(), target.profile);
        if (!proxyEnabled(client, session, id)) {
            List<String> cidrs = cloud.trustedSubnets(session, cluster, managedHostnames(client), true);
            // A durable write-ahead marker prevents blind repetition after an HTTP timeout or AM restart.
            var maps = client.configMaps().inNamespace("kube-system");
            if (maps.withName(INTENT).get() != null) throw new IllegalArgumentException("Previous PROXY update outcome is unresolved. Operator review is required before another LB update.");
            maps.resource(new ConfigMapBuilder().withNewMetadata().withName(INTENT).withLabels(Map.of(IbmCertificateManager.OWNER,"true")).endMetadata()
                    .withData(Map.of("clusterId",id,"requestedAt",Instant.now().toString(),"trustedCidrs",String.join(",",cidrs),"state","SUBMITTED")).build()).create();
            progress.accept("Enabling PROXY protocol; IBM is replacing shared load balancers…");
            cloud.enableProxy(session, id, cidrs);
        } else progress.accept("Waiting for IBM load balancer and controller readiness…");
        certificates.await(() -> {
            try { IbmIngressSupport.verify(client, config); return true; }
            catch (IllegalArgumentException e) { return false; }
        }, "IBM PROXY setup is not ready yet. No application was exposed. Inspect IBM Ingress status; retry will not repeat an accepted LB update.");
        // Keep the marker as an audit/replay guard. A later disable must be reviewed explicitly.
    }

    private ConfigMap acquire(KubernetesClient client, String clusterId) {
        IbmCloudIngressApi.requireId(clusterId);
        var maps = client.configMaps().inNamespace("kube-system");
        var current = maps.withName(LOCK).get();
        String holder = UUID.randomUUID().toString();
        var desired = new ConfigMapBuilder().withNewMetadata().withName(LOCK).withLabels(Map.of(IbmCertificateManager.OWNER,"true")).endMetadata()
                .withData(Map.of("holder",holder,"clusterId",clusterId,"expiresAt",Instant.now().plusSeconds(12600).toString())).build();
        try {
            if (current == null) return maps.resource(desired).create();
            if (!IbmCertificateManager.owned(current) || !clusterId.equals(current.getData().get("clusterId"))
                    || Instant.parse(current.getData().get("expiresAt")).isAfter(Instant.now()))
                throw new IllegalArgumentException("Another Ingress preparation is in progress on this cluster. Retry when it finishes.");
            desired.getMetadata().setResourceVersion(current.getMetadata().getResourceVersion());
            return maps.resource(desired).lockResourceVersion(current.getMetadata().getResourceVersion()).replace();
        } catch (KubernetesClientException e) {
            throw new IllegalArgumentException("Cannot acquire the cluster preparation lock. Check kube-system ConfigMap permissions or another active preparation.");
        }
    }

    static Set<String> managedHostnames(KubernetesClient client) {
        Set<String> result = new HashSet<>();
        for (var prefix : List.of("public-","private-")) for (var service : IbmIngressSupport.managedServices(client, prefix)) {
            if (service.getStatus() == null || service.getStatus().getLoadBalancer() == null
                    || service.getStatus().getLoadBalancer().getIngress() == null || service.getStatus().getLoadBalancer().getIngress().isEmpty())
                throw new IllegalArgumentException("All shared IBM LBs must have ready hostnames before automatic PROXY setup.");
            for (var endpoint : service.getStatus().getLoadBalancer().getIngress()) {
                String host = endpoint.getHostname();
                if (host == null || !host.endsWith(".lb.appdomain.cloud")) throw new IllegalArgumentException("Cannot identify the managed IBM VPC load balancer hostname.");
                result.add(host);
            }
        }
        return result;
    }

    private boolean proxyEnabled(KubernetesClient client, IbmCloudIngressApi.Session session, String id) {
        Set<Boolean> states = new HashSet<>();
        for (String type : List.of("public", "private")) {
            if (!IbmIngressSupport.managedServices(client, type + "-").isEmpty())
                states.add(cloud.proxyConfig(session, id, type).path("proxyProtocol").path("enable").asBoolean());
        }
        if (states.size() != 1) throw new IllegalArgumentException("Shared IBM LBs have mixed PROXY settings. Ask the cluster operator to align them; AM will not overwrite a partial configuration.");
        return states.iterator().next();
    }
    private static void requireProxyProfile(IbmIngressAutomationProperties.Profile profile) {
        if (profile == null || !profile.isAllowSharedLbChanges()) throw new IllegalArgumentException("IBM PROXY protocol is not ready. An operator must authorize shared LB changes in the Project/connection automation profile.");
    }
    private static void verifyRegion(String actual, IbmIngressAutomationProperties.Profile profile) {
        if (!Objects.equals(actual,profile.getRegion())) throw new IllegalArgumentException("The IBM automation profile region does not match the selected cluster.");
    }
    private static DeploymentConfigDTO https(DeploymentConfigDTO config) { return config.toBuilder().ingressTlsEnabled(true).build(); }
}
