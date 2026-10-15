package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClassBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.util.*;
import org.junit.jupiter.api.Test;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;

class IbmTraefikIngressTest {
    DeploymentConfigDTO config() {
        var config = HelmIngressValuesTest.config(); config.setIngressClass(IbmIngressSupport.PUBLIC_TRAEFIK_CLASS);
        config.setIngressHost("notebook.example.com"); config.setIngressTlsEnabled(false); return config;
    }
    void cluster(io.fabric8.kubernetes.client.KubernetesClient client, boolean proxy) {
        String clazz = IbmIngressSupport.PUBLIC_TRAEFIK_CLASS;
        client.network().v1().ingressClasses().resource(new IngressClassBuilder().withNewMetadata().withName(clazz).endMetadata()
                .withNewSpec().withController("traefik.io/ingress-controller").endSpec().build()).create();
        var args = new ArrayList<>(List.of("--providers.kubernetesingress=true", "--providers.kubernetescrd=true",
                "--providers.kubernetesingress.ingressendpoint.publishedservice=kube-system/public-traefik"));
        if (proxy) args.addAll(List.of("--entryPoints.web.proxyProtocol.trustedIPs=10.2.0.0/24", "--entryPoints.websecure.proxyProtocol.trustedIPs=10.2.0.0/24"));
        client.apps().deployments().inNamespace("kube-system").resource(new DeploymentBuilder().withNewMetadata().withName("traefik")
                .withLabels(Map.of("ingress-class",clazz)).endMetadata().withNewSpec().withNewTemplate().withNewSpec()
                .addNewContainer().withName("traefik").withImage("ibm/traefik:3.7.12").withArgs(args).endContainer()
                .endSpec().endTemplate().endSpec().withNewStatus().withAvailableReplicas(1).endStatus().build()).create();
        client.services().inNamespace("kube-system").resource(new ServiceBuilder().withNewMetadata().withName("public-traefik")
                .withLabels(Map.of("app.kubernetes.io/part-of","managed-ingress"))
                .withAnnotations(Map.of(IbmIngressSupport.PROXY_FEATURE,"proxy-protocol")).endMetadata()
                .withNewSpec().withType("LoadBalancer").addNewPort().withPort(80).endPort().endSpec()
                .withNewStatus().withNewLoadBalancer().addNewIngress().withHostname("test.lb.appdomain.cloud").endIngress().endLoadBalancer().endStatus().build()).create();
    }
    @Test void selectsExistingTraefikAndChecksRealSourceIpConfiguration() {
        var server = new KubernetesServer(false,true); server.before();
        try (var client = server.getClient()) {
            cluster(client,true);
            var requested = config(); requested.setIngressClass(IbmIngressSupport.PUBLIC_CLASS);
            assertThat(IbmIngressSupport.resolveAvailable(client,requested).getIngressClass()).isEqualTo(IbmIngressSupport.PUBLIC_TRAEFIK_CLASS);
            assertThat(IbmIngressSupport.verify(client,config())).singleElement().asString().contains("Traefik");
            var nginx = new IngressClassBuilder().withNewMetadata().withName(IbmIngressSupport.PUBLIC_CLASS).endMetadata()
                    .withNewSpec().withController("cloud.ibm.com/"+IbmIngressSupport.PUBLIC_CLASS).endSpec().build();
            client.network().v1().ingressClasses().resource(nginx).create();
            assertThat(IbmIngressSupport.resolveAvailable(client,requested).getIngressClass()).isEqualTo(IbmIngressSupport.PUBLIC_CLASS);
            var deployment=client.apps().deployments().inNamespace("kube-system").withName("traefik").get();
            deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs().removeIf(a->a.toLowerCase().contains("proxyprotocol"));
            client.apps().deployments().inNamespace("kube-system").resource(deployment).update();
            assertThatThrownBy(()->IbmIngressSupport.verify(client,config())).isInstanceOf(IbmIngressSupport.MissingProxyException.class);
        } finally {server.after();}
    }
    @Test void jupyterRendersNativeCidrMiddlewareAndKeepsTlsOnTheSelectedEntryPoint() throws Exception {
        var service=new K8sJupyterService(null,null,null,null,null,null,new ObjectMapper(),null,null);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"gatewayUrl","https://am.example.test/applications/object-storage-gateway");
        var catalog=new SoftwareCatalog(); catalog.setPackageInfo(PackageInfo.builder().packageName("quay.io/jupyter/scipy-notebook").packageVersion("2026-07-28").build());
        var request=new K8sJupyterServiceTest().request(); request.setIngressClass(IbmIngressSupport.PUBLIC_TRAEFIK_CLASS); request.setIngressTlsEnabled(true);request.setIngressTlsSecret("notebook-tls");
        var resources=service.resources(request,catalog,"mcmp-jupyter-91","grant","login");
        var ingress=(Ingress)resources.stream().filter(Ingress.class::isInstance).findFirst().orElseThrow();
        var middleware=(GenericKubernetesResource)resources.stream().filter(GenericKubernetesResource.class::isInstance).findFirst().orElseThrow();
        assertThat(ingress.getMetadata().getAnnotations()).containsEntry("traefik.ingress.kubernetes.io/router.entrypoints","websecure")
                .containsEntry("traefik.ingress.kubernetes.io/router.tls","true").doesNotContainKey(K8sIngressAccessService.CIDR_ANNOTATION);
        assertThat(ingress.getSpec().getTls()).singleElement().satisfies(tls->assertThat(tls.getSecretName()).isEqualTo("notebook-tls"));
        assertThat(new ObjectMapper().writeValueAsString(middleware)).contains("ipAllowList",request.getServicePortCidr());
        assertThat(middleware.getMetadata().getLabels()).containsEntry(K8sJupyterService.OWNER,"mcmp-jupyter-91");
        K8sIngressPolicy.verifyManifest(new org.yaml.snakeyaml.Yaml().dump(new ObjectMapper().convertValue(ingress,Map.class)),request.getServicePortCidr(),request.getIngressHost(),request.getIngressClass());
        ingress.getMetadata().getAnnotations().remove(IbmTraefikIngress.MIDDLEWARES);
        assertThatThrownBy(()->K8sIngressPolicy.verifyManifest(new org.yaml.snakeyaml.Yaml().dump(new ObjectMapper().convertValue(ingress,Map.class)),request.getServicePortCidr(),request.getIngressHost(),request.getIngressClass())).hasMessageContaining("CIDR restriction");
    }
    @Test void helmPolicyIsBoundToItsIngressAndCannotOverwriteAnExistingPolicy() {
        var server=new KubernetesServer(false,true);server.before();
        try(var client=server.getClient()) {
            var cfg=config();String cidr="203.0.113.4/32";
            var policy=IbmTraefikIngress.create(client,cfg,cidr);
            assertThatThrownBy(()->IbmTraefikIngress.create(client,cfg,cidr)).hasMessageContaining("already exists");
            var ingress=new io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder().withNewMetadata().withName("helm-notebook").withNamespace("default")
                    .withAnnotations(Map.of("meta.helm.sh/release-name","notebook",IbmTraefikIngress.MIDDLEWARES,
                            IbmTraefikIngress.reference("default",cfg.getIngressHost(),cfg.getIngressClass(),cidr))).endMetadata().build();
            ingress=client.network().v1().ingresses().inNamespace("default").resource(ingress).create();
            IbmTraefikIngress.bind(client,policy,"notebook");
            var stored=client.genericKubernetesResources(IbmTraefikIngress.CONTEXT).inNamespace("default").withName(policy.getMetadata().getName()).get();
            String uid=ingress.getMetadata().getUid();
            assertThat(stored.getMetadata().getOwnerReferences()).singleElement().satisfies(owner->assertThat(owner.getUid()).isEqualTo(uid));
            assertThatThrownBy(()->IbmTraefikIngress.bind(client,policy,"another-release")).hasMessageContaining("no protected");
        } finally {server.after();}
    }
}
