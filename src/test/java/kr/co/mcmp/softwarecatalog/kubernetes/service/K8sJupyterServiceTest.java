package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.model.PackageInfo;

class K8sJupyterServiceTest {
    @Test void sshManifestKeepsKeysOutOfNotebookAndDoesNotPublishSsh() throws Exception {
        var service=new K8sJupyterService(null,null,null,null,null,null,new ObjectMapper(),null,IbmIngressAutomationTestSupport.legacy());
        var catalog=new SoftwareCatalog();
        catalog.setPackageInfo(PackageInfo.builder().packageName("quay.io/jupyter/scipy-notebook").packageVersion("2026-07-28").build());
        var secret=new SecretBuilder().withNewMetadata().withName("mcmp-jupyter-41-ssh").withNamespace("default").endMetadata()
                .addToStringData("client-key","PRIVATE-CLIENT").addToStringData("host-key","PRIVATE-HOST").build();
        var resources=service.resources(request(),catalog,"mcmp-jupyter-41","grant-token","login-token",secret);
        var deployment=(Deployment)resources.stream().filter(Deployment.class::isInstance).findFirst().orElseThrow();
        var pod=deployment.getSpec().getTemplate().getSpec();
        assertThat(pod.getContainers()).hasSize(2);
        assertThat(deployment.toString()).doesNotContain("PRIVATE-CLIENT","PRIVATE-HOST","grant-token","login-token");
        var ssh=pod.getContainers().stream().filter(c->"ssh-tunnel".equals(c.getName())).findFirst().orElseThrow();
        assertThat(ssh.getEnvFrom()).isEmpty(); assertThat(ssh.getPorts()).isEmpty();
        assertThat(ssh.getImage()).isEqualTo(K8sSshSidecar.DEFAULT_IMAGE).contains("@sha256:");
        assertThat(ssh.getCommand()).containsExactly("/bin/sh", "/opt/mcmp-ssh/start.sh");
        assertThat(ssh.getSecurityContext().getReadOnlyRootFilesystem()).isTrue();
        var sshConfig=(ConfigMap)resources.stream().filter(r->r instanceof ConfigMap && r.getMetadata().getName().endsWith("-ssh")).findFirst().orElseThrow();
        assertThat(sshConfig.getImmutable()).isTrue();
        assertThat(sshConfig.getData().get("sshd_config")).contains("MaxSessions 0", "ListenAddress 127.0.0.1", "PermitListen 127.0.0.1:18084", "PasswordAuthentication no");
        assertThat(sshConfig.toString()).doesNotContain("PRIVATE-CLIENT", "PRIVATE-HOST", "grant-token", "login-token");
        assertThat(ssh.getVolumeMounts()).filteredOn(v->"/etc/shadow".equals(v.getMountPath()))
                .allSatisfy(v->{assertThat(v.getSubPath()).isEqualTo("shadow"); assertThat(v.getReadOnly()).isTrue();});
        assertThat(pod.getVolumes().stream().filter(v->v.getSecret()!=null).findFirst().orElseThrow().getSecret().getItems())
                .extracting(KeyToPath::getKey).containsExactly("host-key","authorized_keys");
        assertThat(pod.getContainers().get(0).getVolumeMounts()).extracting(VolumeMount::getName).doesNotContain("ssh-credentials");
        var gateway=(Secret)resources.stream().filter(r->r instanceof Secret && r!=secret).findFirst().orElseThrow();
        assertThat(gateway.getStringData().get("MCMP_OBJECT_STORAGE_GATEWAY_URL")).isEqualTo("http://127.0.0.1:18084/applications/object-storage-gateway");
        assertThat(service.useSshTunnel()).isTrue();
        ReflectionTestUtils.setField(service,"transport","DIRECT"); assertThat(service.useSshTunnel()).isFalse();
    }
    @Test void lifecycleLookupExcludesRejectedRequestsWithoutManagedRelease() {
        var histories = mock(kr.co.mcmp.softwarecatalog.application.repository.DeploymentHistoryRepository.class);
        var service = new K8sJupyterService(null, null, null, null, histories, null, new ObjectMapper(), null,IbmIngressAutomationTestSupport.legacy());
        service.latest("default", "cluster-a", 41L);
        verify(histories).findTopByCatalogIdAndClusterNameAndNamespaceAndActionTypeAndReleaseNameStartingWithOrderByExecutedAtDesc(
                41L, "cluster-a", "default", kr.co.mcmp.softwarecatalog.application.constants.ActionType.INSTALL, "mcmp-jupyter-");
        verifyNoMoreInteractions(histories);
    }

    DeploymentRequest request() {
        return DeploymentRequest.builder().namespace("default").clusterName("cluster-a")
                .ingressEnabled(true).ingressHost("jupyter.example.test").ingressPath("/")
                .ingressClass("nginx").servicePortCidr("203.0.113.8/32").minReplicas(1)
                .additionalConfig(Map.of("storageClass", "standard")).build();
    }

    @Test void validatesDedicatedHostAndPodReachableGateway() {
        assertThatCode(() -> K8sJupyterService.validate(request(), "https://am.example.test/applications/object-storage-gateway")).doesNotThrowAnyException();
        assertThatCode(() -> K8sJupyterService.validate(request(), "http://am.default.svc/applications/object-storage-gateway")).doesNotThrowAnyException();
    }

    @Test void allowsExternalHttpOnlyWithExplicitTestSetting() {
        String gateway = "http://210.217.178.130:18084/applications/object-storage-gateway";
        assertThatThrownBy(() -> K8sJupyterService.validate(request(), gateway, false))
                .hasMessageContaining("OBJECT_STORAGE_K8S_ALLOW_HTTP");
        assertThatCode(() -> K8sJupyterService.validate(request(), gateway, true)).doesNotThrowAnyException();
    }

    @ParameterizedTest @ValueSource(strings={"", "http://localhost:18084", "http://127.0.0.1:18084", "http://[::1]:18084", "http://user:password@am.example.test", "http://am.example.test?token=secret", "ftp://am.example.test"})
    void httpTestSettingStillRejectsInvalidGateway(String gateway) {
        assertThatThrownBy(() -> K8sJupyterService.validate(request(), gateway, true)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings={"", "http://127.0.0.1:18084", "http://am.example.test", "https://user:password@am.example.test", "https://am.example.test?token=secret"})
    void rejectsInsecureOrVmOnlyGateway(String url) {
        assertThatThrownBy(() -> K8sJupyterService.validate(request(), url)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest @ValueSource(strings={"", "0.0.0.0/0", "1.2.3.999/32", "203.0.113.1", "203.0.113.1/32,0.0.0.0/0"})
    void rejectsMissingOrUnrestrictedCidr(String cidr) {
        var r = request(); r.setServicePortCidr(cidr);
        assertThatThrownBy(() -> K8sJupyterService.validate(r, "https://am.example.test")).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest @ValueSource(strings={"*", "*.example.test", "https://jupyter.test", "jupyter.test:30880", "a..test", "-a.test", "203.0.113.8"})
    void rejectsInvalidOrWildcardHost(String host) {
        var r = request(); r.setIngressHost(host);
        assertThatThrownBy(() -> K8sJupyterService.validate(r, "https://am.example.test")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void refusesIncompatibleRoutingAndScaling() {
        var r=request(); r.setServicePort(30880);
        assertThatThrownBy(() -> K8sJupyterService.validate(r,"https://am.example.test")).hasMessageContaining("service port 8888");
        r.setServicePort(8888); r.setIngressPath("/jupyter");
        assertThatThrownBy(() -> K8sJupyterService.validate(r,"https://am.example.test")).hasMessageContaining("dedicated hostname");
        r.setIngressPath("/"); r.setMinReplicas(2);
        assertThatThrownBy(() -> K8sJupyterService.validate(r,"https://am.example.test")).hasMessageContaining("one replica");
        r.setMinReplicas(1); r.setIngressClass("traefik");
        assertThatThrownBy(() -> K8sJupyterService.validate(r,"https://am.example.test")).hasMessageContaining("nginx");
    }

    @Test void ibmJupyterUsesTheSelectedManagedTlsSecretWithoutChangingThePodProtocol() throws Exception {
        var service = new K8sJupyterService(null,null,null,null,null,null,new ObjectMapper(),null,IbmIngressAutomationTestSupport.legacy());
        ReflectionTestUtils.setField(service, "gatewayUrl", "https://gateway.example.com");
        var catalog = new SoftwareCatalog();
        catalog.setPackageInfo(PackageInfo.builder().packageName("quay.io/jupyter/scipy-notebook").packageVersion("2026-07-28").build());
        var r = request(); r.setIngressClass(IbmIngressSupport.PUBLIC_CLASS);
        r.setIngressTlsEnabled(true); r.setIngressTlsSecret("ibm-existing-cert");
        K8sJupyterService.validate(r, "https://gateway.example.com");
        var resources = service.resources(r, catalog, "mcmp-jupyter-41", "grant-token", "login-token");
        var ingress = (Ingress) resources.stream().filter(Ingress.class::isInstance).findFirst().orElseThrow();
        assertThat(ingress.getSpec().getTls()).singleElement().satisfies(tls -> {
            assertThat(tls.getSecretName()).isEqualTo("ibm-existing-cert");
            assertThat(tls.getHosts()).containsExactly(r.getIngressHost());
        });
        assertThat(ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getPort().getNumber()).isEqualTo(8888);
        r.setIngressClass("nginx");
        assertThatThrownBy(() -> K8sJupyterService.validate(r, "https://gateway.example.com")).hasMessageContaining("HTTP NodePort");
    }

    @Test void manifestsUseExistingNotebookSecretAndIngressOnly() throws Exception {
        var service=new K8sJupyterService(null,null,null,null,null,null,new ObjectMapper(), null,IbmIngressAutomationTestSupport.legacy());
        ReflectionTestUtils.setField(service,"gatewayUrl","https://am.example.test/applications/object-storage-gateway");
        var catalog=new SoftwareCatalog();
        catalog.setPackageInfo(PackageInfo.builder().packageName("quay.io/jupyter/scipy-notebook").packageVersion("2026-07-28").build());
        var resources=service.resources(request(),catalog,"mcmp-jupyter-41","grant-secret","login-secret");
        assertThat(resources).hasSize(6);
        var secret=(Secret)resources.stream().filter(Secret.class::isInstance).findFirst().orElseThrow();
        assertThat(secret.getStringData()).containsEntry("MCMP_OBJECT_STORAGE_TOKEN","grant-secret");
        assertThat(secret.getStringData()).containsEntry("JUPYTER_TOKEN","login-secret");
        var deployment=(Deployment)resources.stream().filter(Deployment.class::isInstance).findFirst().orElseThrow();
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(1);
        assertThat(deployment.getSpec().getTemplate().getSpec().getAutomountServiceAccountToken()).isFalse();
        assertThat(deployment.toString()).doesNotContain("grant-secret","login-secret");
        assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnvFrom().get(0).getSecretRef().getName()).isEqualTo("mcmp-jupyter-41");
        var svc=(Service)resources.stream().filter(Service.class::isInstance).findFirst().orElseThrow();
        assertThat(svc.getSpec().getType()).isEqualTo("ClusterIP");
        assertThat(svc.getSpec().getPorts().get(0).getNodePort()).isNull();
        var ingress=(Ingress)resources.get(5);
        assertThat(ingress.getMetadata().getAnnotations()).containsEntry(K8sIngressAccessService.CIDR_ANNOTATION,"203.0.113.8/32");
        assertThat(ingress.getSpec().getRules().get(0).getHost()).isEqualTo("jupyter.example.test");
        var cm=(ConfigMap)resources.stream().filter(ConfigMap.class::isInstance).findFirst().orElseThrow();
        try(var input=new ClassPathResource("notebooks/object-storage.ipynb").getInputStream()) {
            assertThat(cm.getData().get("sample-data.ipynb")).isEqualTo(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        assertThat(cm.toString()).doesNotContain("grant-secret","login-secret");
        var pvc=(PersistentVolumeClaim)resources.stream().filter(PersistentVolumeClaim.class::isInstance).findFirst().orElseThrow();
        assertThat(pvc.getSpec().getStorageClassName()).isEqualTo("standard");
    }
}
