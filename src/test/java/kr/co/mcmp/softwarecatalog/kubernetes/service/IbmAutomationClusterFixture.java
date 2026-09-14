package kr.co.mcmp.softwarecatalog.kubernetes.service;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClassBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinitionBuilder;
import java.util.*;

/** Real Fabric8 HTTP serialization against an in-memory Kubernetes API, never an external cluster. */
final class IbmAutomationClusterFixture implements AutoCloseable {
    final KubernetesServer server = new KubernetesServer(false,true);
    final KubernetesClient client;
    IbmAutomationClusterFixture(boolean proxy) {
        server.before(); client=server.getClient();
        server.expect().post().withPath("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")
                .andReturn(201,new io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder()
                        .withNewStatus().withAllowed(true).endStatus().build()).always();
        for (var ns : List.of("default","kube-system","cert-manager")) client.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName(ns).endMetadata().build()).create();
        String clazz=IbmIngressSupport.PUBLIC_CLASS;
        client.network().v1().ingressClasses().resource(new IngressClassBuilder().withNewMetadata().withName(clazz).endMetadata()
                .withNewSpec().withController("cloud.ibm.com/"+clazz).endSpec().build()).create();
        client.apps().deployments().inNamespace("kube-system").resource(new DeploymentBuilder().withNewMetadata().withName("public-alb")
                .withLabels(Map.of("ingress-class",clazz)).endMetadata().withNewSpec().withNewTemplate().withNewSpec()
                .addNewContainer().withName("nginx").withArgs("--default-ssl-certificate=default/"+IbmIngressTlsResolverTest.SECRET).endContainer()
                .endSpec().endTemplate().endSpec().withNewStatus().withAvailableReplicas(2).endStatus().build()).create();
        client.services().inNamespace("kube-system").resource(new ServiceBuilder().withNewMetadata().withName("public-alb")
                .withLabels(Map.of("app.kubernetes.io/part-of","managed-ingress"))
                .withAnnotations(Map.of(IbmIngressSupport.PROXY_FEATURE,proxy?"proxy-protocol":"alb")).endMetadata()
                .withNewSpec().withType("LoadBalancer").addNewPort().withPort(443).endPort().endSpec()
                .withNewStatus().withNewLoadBalancer().addNewIngress().withHostname("a.lb.appdomain.cloud").endIngress().endLoadBalancer().endStatus().build()).create();
        client.configMaps().inNamespace("kube-system").resource(new ConfigMapBuilder().withNewMetadata().withName("ibm-k8s-controller-config").endMetadata()
                .withData(Map.of("use-proxy-protocol",String.valueOf(proxy),"proxy-real-ip-cidr","10.2.0.0/24")).build()).create();
        client.secrets().inNamespace("default").resource(IbmIngressTlsResolverTest.certificate(IbmIngressTlsResolverTest.SECRET,
                IbmIngressTlsResolverTest.RSA,IbmIngressTlsResolverTest.DOMAIN,"*."+IbmIngressTlsResolverTest.DOMAIN)).create();
    }
    void proxyReady() {
        var cm=client.configMaps().inNamespace("kube-system").withName("ibm-k8s-controller-config").get();
        cm.setData(Map.of("use-proxy-protocol","true","proxy-real-ip-cidr","10.2.0.0/24")); client.configMaps().inNamespace("kube-system").resource(cm).update();
        var service=client.services().inNamespace("kube-system").withName("public-alb").get();
        service.getMetadata().setAnnotations(Map.of(IbmIngressSupport.PROXY_FEATURE,"proxy-protocol")); client.services().inNamespace("kube-system").resource(service).update();
    }
    void certManagerPresent() {
        for (String definition: List.of("certificates.cert-manager.io","certificaterequests.cert-manager.io","issuers.cert-manager.io",
                "clusterissuers.cert-manager.io","orders.acme.cert-manager.io","challenges.acme.cert-manager.io")) {
            String plural=definition.substring(0,definition.indexOf('.')); String group=definition.substring(definition.indexOf('.')+1);
            client.apiextensions().v1().customResourceDefinitions().resource(new CustomResourceDefinitionBuilder().withNewMetadata().withName(definition).endMetadata()
                    .withNewSpec().withGroup(group).withScope("Namespaced").withNewNames().withPlural(plural).withKind(plural).endNames()
                    .addNewVersion().withName("v1").withServed(true).withStorage(true).endVersion().endSpec().build()).create();
        }
    }
    public void close() { client.close(); server.after(); }
}
