package kr.co.mcmp.softwarecatalog.kubernetes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Serialization;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubeconfigResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class IbmCertificateManager {
    static final String NS = "cert-manager";
    static final String OWNER = "mcmp.io/ingress-automation";
    static final ResourceDefinitionContext CERT = definition("Certificate", "certificates", true);
    static final ResourceDefinitionContext ISSUER = definition("ClusterIssuer", "clusterissuers", false);
    private static final List<String> CRDS = List.of("certificates.cert-manager.io", "certificaterequests.cert-manager.io",
            "issuers.cert-manager.io", "clusterissuers.cert-manager.io", "orders.acme.cert-manager.io", "challenges.acme.cert-manager.io");
    private final IbmIngressAutomationProperties properties;
    private final KubeconfigResolver kubeconfigs;
    private final ObjectMapper json = new ObjectMapper();

    static class Plan {
        GenericKubernetesResource issuer;
        List<Secret> secrets;
        boolean install;
    }

    Plan plan(KubernetesClient client, IbmIngressAutomationProperties.Profile profile, String host) {
        if (profile == null || !profile.allowsHost(host))
            throw new IllegalArgumentException("This custom domain is not authorized for automatic certificates. Configure the Project's IBM automation profile.");
        Plan plan = new Plan();
        try {
            plan.issuer = Serialization.unmarshal(IbmIngressAutomationProperties.readOperatorFile(profile.getIssuerTemplateFile(), "ACME ClusterIssuer template"), GenericKubernetesResource.class);
            var tree = json.valueToTree(plan.issuer);
            var acme = tree.path("spec").path("acme");
            if (!"cert-manager.io/v1".equals(plan.issuer.getApiVersion()) || !"ClusterIssuer".equals(plan.issuer.getKind())
                    || !validName(plan.issuer.getMetadata().getName()) || plan.issuer.getMetadata().getNamespace() != null
                    || acme.path("email").asText().isBlank()
                    || !Set.of("https://acme-v02.api.letsencrypt.org/directory", "https://acme-staging-v02.api.letsencrypt.org/directory").contains(acme.path("server").asText())
                    || !validName(acme.path("privateKeySecretRef").path("name").asText())
                    || !acme.path("solvers").isArray() || acme.path("solvers").isEmpty()) throw new IllegalArgumentException();
            for (var solver : acme.path("solvers")) {
                if (!solver.path("dns01").isObject() || solver.path("dns01").isEmpty() || solver.has("http01")) throw new IllegalArgumentException();
            }
            plan.issuer.getAdditionalProperties().remove("status");
            plan.issuer.getMetadata().setResourceVersion(null);
            plan.issuer.getMetadata().setUid(null);
            plan.issuer.getMetadata().setOwnerReferences(null);
            plan.issuer.getMetadata().setLabels(Map.of(OWNER, "true"));
            plan.secrets = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (String path : profile.getDnsSecretFiles()) {
                Secret secret = Serialization.unmarshal(IbmIngressAutomationProperties.readOperatorFile(path, "DNS credential Secret"), Secret.class);
                if (!"v1".equals(secret.getApiVersion()) || !"Secret".equals(secret.getKind()) || !"Opaque".equals(secret.getType())
                        || !NS.equals(secret.getMetadata().getNamespace()) || !validName(secret.getMetadata().getName())
                        || !names.add(secret.getMetadata().getName())) throw new IllegalArgumentException();
                if (secret.getData() == null) secret.setData(new HashMap<>());
                if (secret.getStringData() != null) secret.getStringData().forEach((k,v) -> secret.getData().put(k, Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8))));
                secret.setStringData(null);
                if (secret.getData().isEmpty()) throw new IllegalArgumentException();
                secret.getMetadata().setLabels(Map.of(OWNER,"true"));
                secret.getMetadata().setOwnerReferences(null);
                secret.getMetadata().setResourceVersion(null);
                secret.getMetadata().setUid(null);
                plan.secrets.add(secret);
            }
        } catch (Exception e) { throw new IllegalArgumentException("Invalid server-side certificate configuration. Use a named Let's Encrypt DNS01 ClusterIssuer and Opaque DNS Secrets in cert-manager namespace."); }

        var definitions = CRDS.stream().map(name -> client.apiextensions().v1().customResourceDefinitions().withName(name).get())
                .filter(Objects::nonNull).toList();
        long present = definitions.size();
        if (present > 0 && present != CRDS.size()) throw new IllegalArgumentException("Partial cert-manager installation detected; ask its operator to repair it. AM will not overwrite it.");
        if (definitions.stream().anyMatch(d -> d.getSpec() == null || d.getSpec().getVersions() == null
                || d.getSpec().getVersions().stream().noneMatch(v -> "v1".equals(v.getName()) && Boolean.TRUE.equals(v.getServed()))))
            throw new IllegalArgumentException("The existing cert-manager installation must serve cert-manager.io/v1 APIs. Ask its operator to upgrade it.");
        plan.install = present == 0;
        boolean createIssuer = plan.install;
        if (plan.install && !profile.isInstallCertManager()) throw new IllegalArgumentException("cert-manager is absent. Enable install-cert-manager in the authorized IBM automation profile.");
        if (plan.install) {
            String minor = client.getKubernetesVersion().getMinor().replaceAll("[^0-9].*$", "");
            int version = Integer.parseInt(minor);
            String chart = properties.getCertManagerVersion();
            if (!(chart.matches("v1\\.20\\.[0-9]+") && version >= 32 && version <= 35
                    || chart.matches("v1\\.21\\.[0-9]+") && version >= 33 && version <= 36))
                throw new IllegalArgumentException("Configured cert-manager chart is not supported for this Kubernetes version. Configure a tested 1.20.x or 1.21.x version.");
            // Never take ownership of a pre-existing installation that happens to lack CRDs.
            if (!client.apps().deployments().inNamespace(NS).list().getItems().isEmpty())
                throw new IllegalArgumentException("Existing cert-manager namespace workloads require operator review before installation.");
            for (var pair : List.of(new String[]{"apiextensions.k8s.io","customresourcedefinitions"},
                    new String[]{"rbac.authorization.k8s.io","clusterroles"}, new String[]{"rbac.authorization.k8s.io","clusterrolebindings"},
                    new String[]{"admissionregistration.k8s.io","mutatingwebhookconfigurations"}, new String[]{"admissionregistration.k8s.io","validatingwebhookconfigurations"}))
                IngressAutomationPermissions.require(client,pair[0],pair[1],null,"create");
            IngressAutomationPermissions.require(client,"","namespaces",null,"create");
            for (String resource : List.of("secrets","services","serviceaccounts"))
                IngressAutomationPermissions.require(client,"",resource,NS,"create");
            IngressAutomationPermissions.require(client,"apps","deployments",NS,"create");
            IngressAutomationPermissions.require(client,"batch","jobs",NS,"create");
            for (String namespace : List.of(NS,"kube-system")) for (String resource : List.of("roles","rolebindings"))
                IngressAutomationPermissions.require(client,"rbac.authorization.k8s.io",resource,namespace,"create");
        } else {
            if (client.namespaces().withName(NS).get() == null)
                throw new IllegalArgumentException("Existing cert-manager must use cert-manager as its ClusterIssuer resource namespace for this automation profile.");
            var existing = client.genericKubernetesResources(ISSUER).withName(plan.issuer.getMetadata().getName()).get();
            createIssuer = existing == null;
            if (existing != null && !sameSpec(existing, plan.issuer)) throw new IllegalArgumentException("The configured ClusterIssuer already exists with different settings. AM will not overwrite it.");
        }
        if (createIssuer) IngressAutomationPermissions.require(client,"cert-manager.io","clusterissuers",null,"create");
        for (Secret desired : plan.secrets) {
            Secret existing = client.secrets().inNamespace(NS).withName(desired.getMetadata().getName()).get();
            if (existing != null && (!Objects.equals(existing.getType(), desired.getType()) || !Objects.equals(existing.getData(), desired.getData())))
                throw new IllegalArgumentException("A DNS credential Secret already exists with different data. Update it through its administrator.");
        }
        // Validate referenced DNS credential keys before installing anything or replacing an LB.
        var staged = new HashMap<String,Secret>(); plan.secrets.forEach(s -> staged.put(s.getMetadata().getName(),s));
        verifyDnsReferences(client, json.valueToTree(plan.issuer).path("spec").path("acme").path("solvers"), staged);
        return plan;
    }

    void prepare(KubernetesClient client, String tbNamespace, String clusterName, String workloadNamespace,
                 String host, Plan plan, Consumer<String> progress) {
        try { provision(client,tbNamespace,clusterName,workloadNamespace,host,plan,progress); }
        catch (KubernetesClientException e) {
            // A rejected Secret create can contain its entire request body. Do not retain the cause in logs or responses.
            throw new IllegalArgumentException("Certificate resources could not be prepared. Check Kubernetes permissions, cert-manager status and server-side DNS configuration (HTTP " + e.getCode() + ").");
        }
    }

    private void provision(KubernetesClient client, String tbNamespace, String clusterName, String workloadNamespace,
                           String host, Plan plan, Consumer<String> progress) {
        if (client.namespaces().withName(workloadNamespace).get() == null)
            throw new IllegalArgumentException("The application Kubernetes namespace must exist before certificate provisioning.");
        if (plan.install) {
            progress.accept("Installing cert-manager in the selected cluster…");
            install(tbNamespace, clusterName);
        }
        for (Secret secret : plan.secrets) {
            var existingSecret = client.secrets().inNamespace(NS).withName(secret.getMetadata().getName()).get();
            if (existingSecret == null) client.secrets().inNamespace(NS).resource(secret).create();
            else if (!Objects.equals(existingSecret.getData(),secret.getData()) || !Objects.equals(existingSecret.getType(),secret.getType()))
                throw new IllegalArgumentException("DNS credential configuration changed during preparation. AM will not overwrite it.");
        }
        var issuers = client.genericKubernetesResources(ISSUER);
        String issuerName = plan.issuer.getMetadata().getName();
        var currentIssuer = issuers.withName(issuerName).get();
        if (currentIssuer == null) issuers.resource(plan.issuer).create();
        else if (!sameSpec(currentIssuer,plan.issuer))
            throw new IllegalArgumentException("ClusterIssuer configuration changed during preparation. AM will not overwrite it.");
        await(() -> ready(issuers.withName(issuerName).get()), "Certificate issuer is not ready. Check cert-manager and DNS credentials.");
        progress.accept("Issuing the domain certificate (DNS validation)…");
        String name = certificateName(host);
        GenericKubernetesResource desired = certificateResource(workloadNamespace, host, issuerName);
        var certificates = client.genericKubernetesResources(CERT).inNamespace(workloadNamespace);
        var existing = certificates.withName(name).get();
        if (existing != null && (!owned(existing) || !sameSpec(existing, desired)))
            throw new IllegalArgumentException("Certificate name is already owned by another configuration. AM will not replace it.");
        if (existing == null) {
            if (client.secrets().inNamespace(workloadNamespace).withName(name).get() != null)
                throw new IllegalArgumentException("The target TLS Secret already exists without an AM certificate. AM will not overwrite it.");
            certificates.resource(desired).create();
        }
        await(() -> ready(certificates.withName(name).get()), "Certificate issuance is still pending or failed. Check DNS01 Challenges and cert-manager; retry reuses the existing request.");
        IbmIngressTlsResolver.verifyHost(IbmIngressTlsResolver.certificate(client, workloadNamespace, name), host);
        registerBinding(client, workloadNamespace, host, name);
    }

    void checkWorkloadPermissions(KubernetesClient client, String workload, Plan plan) {
        IngressAutomationPermissions.lock(client);
        IngressAutomationPermissions.require(client,"cert-manager.io","certificates",workload,"create");
        for (String verb : List.of("get","create","update")) IngressAutomationPermissions.require(client,"","configmaps",workload,verb);
        if (!plan.secrets.isEmpty()) IngressAutomationPermissions.require(client,"","secrets",NS,"create");
    }

    private static void verifyDnsReferences(KubernetesClient client, com.fasterxml.jackson.databind.JsonNode node, Map<String,Secret> staged) {
        if (node.isArray()) { node.forEach(n -> verifyDnsReferences(client,n,staged)); return; }
        if (!node.isObject()) return;
        var fields=node.fields();
        while (fields.hasNext()) {
            var entry=fields.next(); var value=entry.getValue();
            if (entry.getKey().toLowerCase(Locale.ROOT).endsWith("secretref") && value.isObject()) {
                String name=value.path("name").asText(), key=value.path("key").asText();
                Secret secret=staged.get(name);
                if (secret==null) secret=client.secrets().inNamespace(NS).withName(name).get();
                if (secret==null || secret.getData()==null || !secret.getData().containsKey(key))
                    throw new IllegalArgumentException("A DNS01 credential Secret/key referenced by the configured issuer is missing in cert-manager namespace.");
            } else verifyDnsReferences(client,value,staged);
        }
    }

    void install(String namespace, String clusterName) {
        Path file = null;
        try {
            file = Files.createTempFile("am-cert-manager-", ".yaml", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.writeString(file, kubeconfigs.getKubeconfigYaml(namespace, clusterName));
            // Dedicated release, never a subchart of an application and never an automatic upgrade.
            var command = List.of(properties.getHelmExecutable(), "install", "am-cert-manager", "oci://quay.io/jetstack/charts/cert-manager",
                    "--version", properties.getCertManagerVersion(), "--namespace", NS, "--create-namespace", "--set", "crds.enabled=true",
                    "--wait", "--timeout", "10m", "--kubeconfig", file.toString());
            var process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try {
                if (!process.waitFor(660, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("cert-manager installation timed out; inspect the existing release before retrying."); }
                if (process.exitValue() != 0) throw new IllegalStateException("cert-manager installation failed. Check cluster-wide install permissions, chart compatibility and Helm release status.");
            } finally { if (process.isAlive()) process.destroyForcibly(); }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("cert-manager installation interrupted; inspect the release before retrying."); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot run Helm for cert-manager installation."); }
        finally { if (file != null) try { Files.deleteIfExists(file); } catch (java.io.IOException ignored) { } }
    }

    static GenericKubernetesResource certificateResource(String ns, String host, String issuer) {
        var resource = new GenericKubernetesResource();
        resource.setApiVersion("cert-manager.io/v1"); resource.setKind("Certificate");
        resource.setMetadata(new ObjectMetaBuilder().withName(certificateName(host)).withNamespace(ns).withLabels(Map.of(OWNER,"true")).build());
        resource.setAdditionalProperty("spec", Map.of("secretName", certificateName(host), "dnsNames", List.of(host),
                "issuerRef", Map.of("name",issuer,"kind","ClusterIssuer","group","cert-manager.io"),
                "privateKey", Map.of("rotationPolicy","Always"), "secretTemplate", Map.of("labels",Map.of(OWNER,"true"))));
        return resource;
    }

    static String certificateName(String host) {
        try { return "am-tls-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(host.getBytes(StandardCharsets.UTF_8))).substring(0,24); }
        catch (Exception e) { throw new IllegalStateException("Cannot calculate certificate identity."); }
    }

    void registerBinding(KubernetesClient client, String namespace, String host, String secret) {
        var maps = client.configMaps().inNamespace(namespace);
        for (int attempt = 0; attempt < 5; attempt++) {
            var existing = maps.withName(IbmIngressTlsResolver.BINDINGS_CONFIG_MAP).get();
            var entries = new LinkedHashMap<>(IbmIngressTlsResolver.bindings(client, namespace));
            String binding = IbmIngressTlsResolver.selectBinding(entries, host);
            if (binding != null) {
                if (!secret.equals(binding)) throw new IllegalArgumentException("A domain binding was added by another operator; AM will not replace it.");
                return;
            }
            entries.put(host, secret);
            try {
                String data = json.writeValueAsString(entries.entrySet().stream().map(e -> Map.of("host",e.getKey(),"secretName",e.getValue())).toList());
                if (existing == null) maps.resource(new ConfigMapBuilder().withNewMetadata().withName(IbmIngressTlsResolver.BINDINGS_CONFIG_MAP).endMetadata()
                        .withData(Map.of("bindings.json",data)).build()).create();
                else {
                    var values = new HashMap<>(existing.getData()); values.put("bindings.json", data); existing.setData(values);
                    maps.resource(existing).lockResourceVersion(existing.getMetadata().getResourceVersion()).replace();
                }
                return;
            } catch (KubernetesClientException e) { if (e.getCode() != 409) throw e; }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Cannot encode domain bindings."); }
        }
        throw new IllegalArgumentException("Domain bindings changed concurrently; retry preparation.");
    }

    void await(java.util.function.BooleanSupplier condition, String failure) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(Math.min(3600, Math.max(1, properties.getReadyTimeoutSeconds())));
        do {
            if (condition.getAsBoolean()) return;
            try { TimeUnit.SECONDS.sleep(Math.min(30,Math.max(1,properties.getPollSeconds()))); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Ingress preparation interrupted; retry checks the existing resources."); }
        } while (System.nanoTime() < deadline);
        throw new IllegalArgumentException(failure);
    }
    static boolean ready(GenericKubernetesResource resource) {
        if (resource == null) return false;
        var tree = new ObjectMapper().valueToTree(resource);
        for (var condition : tree.path("status").path("conditions"))
            if ("Ready".equals(condition.path("type").asText()) && "True".equals(condition.path("status").asText())
                    && condition.path("observedGeneration").asLong(-1) >= tree.path("metadata").path("generation").asLong(0)) return true;
        return false;
    }
    static boolean owned(HasMetadata resource) { return resource.getMetadata().getLabels() != null && "true".equals(resource.getMetadata().getLabels().get(OWNER)); }
    static boolean sameSpec(GenericKubernetesResource a, GenericKubernetesResource b) {
        // The admission webhook may add defaults. Compare every requested field, but accept unrelated defaults.
        return contains(new ObjectMapper().valueToTree(a).path("spec"), new ObjectMapper().valueToTree(b).path("spec"));
    }
    private static boolean contains(com.fasterxml.jackson.databind.JsonNode actual, com.fasterxml.jackson.databind.JsonNode desired) {
        if (desired.isArray()) {
            if (!actual.isArray() || actual.size() != desired.size()) return false;
            for (int i=0;i<desired.size();i++) if (!contains(actual.get(i),desired.get(i))) return false;
            return true;
        }
        if (!desired.isObject()) return actual.equals(desired);
        if (!actual.isObject()) return false;
        var fields = desired.fields();
        while (fields.hasNext()) { var entry = fields.next(); if (!contains(actual.path(entry.getKey()),entry.getValue())) return false; }
        return true;
    }
    static boolean validName(String value) { return value != null && value.length() <= 63 && value.matches("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"); }
    private static ResourceDefinitionContext definition(String kind, String plural, boolean namespaced) {
        return new ResourceDefinitionContext.Builder().withGroup("cert-manager.io").withVersion("v1").withKind(kind).withPlural(plural).withNamespaced(namespaced).build();
    }
}
