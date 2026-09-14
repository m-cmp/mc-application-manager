package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServiceListBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentListBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressClassBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.service.VmSecurityGroupExposureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IbmIngressSupportTest {
    static K8sClusterDto cluster(String provider) {
        var c = new K8sClusterDto(); var connection = new K8sClusterDto.ConnectionConfig();
        connection.setProviderName(provider); c.setConnectionConfig(connection); return c;
    }

    @ParameterizedTest @ValueSource(strings={"ibm", "IBM", "ibmcloud", "ibm-cloud", "ibm-vpc", "ibmvpc"})
    void mapsExistingCatalogClassWithoutChangingCatalog(String provider) {
        var config = HelmIngressValuesTest.config(); config.setIngressClass("nginx");
        assertThat(IbmIngressSupport.resolve(cluster(provider), config).getIngressClass()).isEqualTo(IbmIngressSupport.PUBLIC_CLASS);
        assertThat(config.getIngressClass()).isEqualTo("nginx");
        config.setIngressClass(IbmIngressSupport.PRIVATE_CLASS);
        assertThat(IbmIngressSupport.resolve(cluster(provider), config).getIngressClass()).isEqualTo(IbmIngressSupport.PRIVATE_CLASS);
    }

    @ParameterizedTest @ValueSource(strings={"aws", "azure", "gcp", "tencent", "ncp"})
    void keepsOtherCspNodePortClassAndRejectsIbmClass(String provider) {
        var config = HelmIngressValuesTest.config(); config.setIngressClass("nginx");
        assertThat(IbmIngressSupport.resolve(cluster(provider), config).getIngressClass()).isEqualTo("nginx");
        config.setIngressClass(IbmIngressSupport.PUBLIC_CLASS);
        assertThatThrownBy(() -> IbmIngressSupport.resolve(cluster(provider), config)).hasMessageContaining("IBM cluster");
    }

    static KubernetesClient readyClient(String clazz, boolean proxy, String trusted, String features, boolean http) {
        var client = mock(KubernetesClient.class, RETURNS_DEEP_STUBS);
        Resource<io.fabric8.kubernetes.api.model.networking.v1.IngressClass> classResource = mock(Resource.class);
        var classes = client.network().v1().ingressClasses();
        doReturn(classResource).when(classes).withName(clazz);
        when(client.network().v1().ingressClasses().withName(clazz).get()).thenReturn(new IngressClassBuilder()
                .withNewMetadata().withName(clazz).endMetadata().withNewSpec().withController("cloud.ibm.com/"+clazz).endSpec().build());
        var deployments = client.apps().deployments();
        var deploymentOps = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        doReturn(deploymentOps).when(deployments).inNamespace("kube-system");
        var filtered = mock(io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable.class);
        doReturn(filtered).when(deploymentOps).withLabel("ingress-class", clazz);
        when(filtered.list())
                .thenReturn(new DeploymentListBuilder().withItems(new DeploymentBuilder().withNewStatus().withAvailableReplicas(2).endStatus().build()).build());
        var service = new ServiceBuilder().withNewMetadata().withName(clazz.startsWith("public")?"public-crtest":"private-crtest")
                .withLabels(Map.of("app.kubernetes.io/part-of","managed-ingress"))
                .withAnnotations(Map.of(IbmIngressSupport.PROXY_FEATURE,features)).endMetadata()
                .withNewSpec().withType("LoadBalancer").addNewPort().withPort(http?80:443).endPort().endSpec()
                .withNewStatus().withNewLoadBalancer().addNewIngress().withHostname("test.lb.appdomain.cloud")
                .endIngress().endLoadBalancer().endStatus().build();
        var services = client.services();
        var serviceOps = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        doReturn(serviceOps).when(services).inNamespace("kube-system");
        when(serviceOps.list()).thenReturn(new ServiceListBuilder().withItems(service).build());
        Resource<io.fabric8.kubernetes.api.model.ConfigMap> configResource = mock(Resource.class);
        var configs = client.configMaps();
        var configMaps = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        doReturn(configMaps).when(configs).inNamespace("kube-system");
        doReturn(configResource).when(configMaps).withName("ibm-k8s-controller-config");
        when(configResource.get())
                .thenReturn(new ConfigMapBuilder().withData(Map.of("use-proxy-protocol",String.valueOf(proxy),"proxy-real-ip-cidr",trusted)).build());
        return client;
    }

    @ParameterizedTest @ValueSource(strings={IbmIngressSupport.PUBLIC_CLASS,IbmIngressSupport.PRIVATE_CLASS})
    void readyManagedAlbsExposeHostnameWithoutNodePortLookup(String clazz) {
        var client = readyClient(clazz,true,"10.150.0.0/24","proxy-protocol",true);
        assertThat(IbmIngressSupport.verify(client,clazz)).singleElement().asString().contains("test.lb.appdomain.cloud");
        verify(client.services(),never()).inAnyNamespace();
    }

    @Test void missingOriginalClientIpStopsDeployment() {
        var client = readyClient(IbmIngressSupport.PUBLIC_CLASS,false,"10.150.0.0/24","",true);
        assertThatThrownBy(() -> IbmIngressSupport.verify(client,IbmIngressSupport.PUBLIC_CLASS)).hasMessageContaining("source IP preservation is disabled");
    }

    @ParameterizedTest @ValueSource(strings={"", "0.0.0.0/0", "::/0", "invalid", "10.0.0.0/0", "10.150.0.0/24,"})
    void doesNotTrustProxyHeadersFromEverySource(String cidr) {
        var client = readyClient(IbmIngressSupport.PUBLIC_CLASS,true,cidr,"proxy-protocol",true);
        assertThatThrownBy(() -> IbmIngressSupport.verify(client,IbmIngressSupport.PUBLIC_CLASS)).hasMessageContaining("proxy-real-ip-cidr");
    }

    @Test void bothLbAndControllerMustUseProxyProtocol() {
        var client = readyClient(IbmIngressSupport.PUBLIC_CLASS,true,"10.150.0.0/24","alb",true);
        assertThatThrownBy(() -> IbmIngressSupport.verify(client,IbmIngressSupport.PUBLIC_CLASS)).hasMessageContaining("does not enable PROXY");
    }

    @Test void httpIsRejectedOnHttpsOnlyLb() {
        var client=readyClient(IbmIngressSupport.PUBLIC_CLASS,true,"10.150.0.0/24","proxy-protocol",false);
        var config=HelmIngressValuesTest.config(); config.setIngressClass(IbmIngressSupport.PUBLIC_CLASS); config.setIngressTlsEnabled(false);
        assertThatThrownBy(() -> IbmIngressSupport.verify(client,config)).hasMessageContaining("does not expose port 80");
        config.setIngressTlsEnabled(true);
        assertThat(IbmIngressSupport.verify(client,config)).isNotEmpty();
    }

    @Test void classMustBelongToIbmController() {
        var client=readyClient(IbmIngressSupport.PUBLIC_CLASS,true,"10.150.0.0/24","proxy-protocol",true);
        when(client.network().v1().ingressClasses().withName(IbmIngressSupport.PUBLIC_CLASS).get())
                .thenReturn(new IngressClassBuilder().withNewSpec().withController("k8s.io/ingress-nginx").endSpec().build());
        assertThatThrownBy(() -> IbmIngressSupport.verify(client,IbmIngressSupport.PUBLIC_CLASS)).hasMessageContaining("unavailable");
    }

    @Test void oldApiClientsGetManagedClassAndNeverOpenWorkerSecurityGroup() {
        var tb=mock(CbtumblebugRestApi.class); var ledger=mock(VmSecurityGroupExposureService.class);
        var resolver=mock(K8sWorkerSecurityGroupResolver.class); var access=new K8sIngressAccessService(tb,ledger,resolver);
        when(tb.getK8sClusterByName("default","ibm-cluster")).thenReturn(cluster("ibm"));
        var request=DeploymentRequest.builder().namespace("default").clusterName("ibm-cluster")
                .ingressEnabled(true).ingressClass("nginx").openServicePort(true).servicePortCidr("203.0.113.8/32").build();
        access.resolveTarget(request,new SoftwareCatalog());
        assertThat(request.getIngressClass()).isEqualTo(IbmIngressSupport.PUBLIC_CLASS);
        access.open(request,null);
        verifyNoInteractions(ledger,resolver);
    }

    @Test void missingOrUnreadyManagedResourcesFailClosed() {
        var client = readyClient(IbmIngressSupport.PUBLIC_CLASS,true,"10.150.0.0/24","proxy-protocol",true);
        var service = client.services().inNamespace("kube-system").list().getItems().get(0);
        service.setStatus(null);
        assertThatThrownBy(() -> IbmIngressSupport.verify(client,IbmIngressSupport.PUBLIC_CLASS)).hasMessageContaining("no ready external endpoint");
        when(client.services().inNamespace("kube-system").list()).thenReturn(new ServiceListBuilder().build());
        assertThatThrownBy(() -> IbmIngressSupport.verify(client,IbmIngressSupport.PUBLIC_CLASS)).hasMessageContaining("No active");
        var other = readyClient(IbmIngressSupport.PUBLIC_CLASS,true,"10.150.0.0/24","proxy-protocol",true);
        when(other.apps().deployments().inNamespace("kube-system").withLabel("ingress-class",IbmIngressSupport.PUBLIC_CLASS).list())
                .thenReturn(new DeploymentListBuilder().build());
        assertThatThrownBy(() -> IbmIngressSupport.verify(other,IbmIngressSupport.PUBLIC_CLASS)).hasMessageContaining("not ready");
    }

    @ParameterizedTest @ValueSource(strings={IbmIngressSupport.PUBLIC_CLASS,IbmIngressSupport.PRIVATE_CLASS})
    void managedRoutesMustKeepCidrAndCannotExposeBypassServices(String clazz) {
        String route = """
                kind: Ingress
                metadata:
                  annotations:
                    kubernetes.io/ingress.class: %s
                    nginx.ingress.kubernetes.io/whitelist-source-range: 203.0.113.8/32
                spec:
                  ingressClassName: %s
                  rules:
                  - host: app.example.com
                """.formatted(clazz,clazz);
        assertThatCode(() -> K8sIngressPolicy.verifyManifest(route,"203.0.113.8/32","app.example.com",clazz)).doesNotThrowAnyException();
        assertThatThrownBy(() -> K8sIngressPolicy.verifyManifest(route.replace("ingressClassName: "+clazz,"ingressClassName: nginx"),"203.0.113.8/32","app.example.com",clazz)).hasMessageContaining("restriction");
        assertThatThrownBy(() -> K8sIngressPolicy.verifyManifest(route.replace("203.0.113.8/32","0.0.0.0/0"),"203.0.113.8/32","app.example.com",clazz)).hasMessageContaining("restriction");
        for (String type : java.util.List.of("NodePort","LoadBalancer")) {
            assertThatThrownBy(() -> K8sIngressPolicy.verifyManifest(route+"\n---\nkind: Service\nspec:\n  type: "+type,"203.0.113.8/32","app.example.com",clazz)).hasMessageContaining("bypassing");
        }
    }
}
