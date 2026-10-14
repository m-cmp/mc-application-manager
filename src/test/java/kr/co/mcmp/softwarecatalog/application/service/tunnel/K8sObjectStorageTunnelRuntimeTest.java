package kr.co.mcmp.softwarecatalog.application.service.tunnel;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class K8sObjectStorageTunnelRuntimeTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"default", "my-project"})
    void restoresTunnelWithProjectCredentialsAndActualWorkloadNamespace(String workloadNamespace) throws Exception {
        var server = new io.fabric8.kubernetes.client.server.mock.KubernetesServer(false, true);
        server.before();
        try {
            var clients = org.mockito.Mockito.mock(kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory.class);
            var client = server.getClient();
            org.mockito.Mockito.when(clients.getClient("my-project", "cluster-a")).thenReturn(client);
            var deployment = client.apps().deployments().resource(new io.fabric8.kubernetes.api.model.apps.DeploymentBuilder()
                    .withNewMetadata().withName("mcmp-jupyter-41").withNamespace(workloadNamespace)
                    .addToLabels(K8sObjectStorageTunnelRuntime.OWNER, "mcmp-jupyter-41").endMetadata()
                    .withNewSpec().withReplicas(1).endSpec().build()).create();
            var secret = client.secrets().resource(new io.fabric8.kubernetes.api.model.SecretBuilder().withNewMetadata()
                    .withName("mcmp-jupyter-41-ssh").withNamespace(workloadNamespace).endMetadata().build()).create();
            var tunnel = new kr.co.mcmp.softwarecatalog.application.model.K8sObjectStorageTunnel();
            tunnel.setNamespace("my-project"); tunnel.setClusterName("cluster-a"); tunnel.setReleaseName("mcmp-jupyter-41");
            tunnel.setWorkloadUid(deployment.getMetadata().getUid()); tunnel.setSecretUid(secret.getMetadata().getUid());
            // No Pod yet: reaching the Pod lookup proves Deployment and Secret identity checks passed.
            assertThatThrownBy(() -> new K8sObjectStorageTunnelRuntime(clients, null).start(tunnel)).isInstanceOf(IllegalStateException.class);
            org.mockito.Mockito.verify(clients).getClient("my-project", "cluster-a");
            assertThat(server.getLastRequest().getPath()).startsWith("/api/v1/namespaces/" + workloadNamespace + "/pods");
        } finally { server.after(); }
    }
    @Test void reverseForwardUsesOnlyPodLoopbackAndPinsHostKey() {
        var args=K8sObjectStorageTunnelRuntime.sshArguments("ssh",41L,Path.of("test-keys"),40123,40124);
        assertThat(args).contains("StrictHostKeyChecking=yes","HostKeyAlias=mcmp-k8s-41","ExitOnForwardFailure=yes",
                "ServerAliveInterval=15","ServerAliveCountMax=3","127.0.0.1:18084:127.0.0.1:40124");
        assertThat(args.get(args.size()-1)).isEqualTo("127.0.0.1");
        assertThat(args).doesNotContain("0.0.0.0", "StrictHostKeyChecking=no");
    }
    @Test void hostKeyDoesNotAcceptInjectedOptions() {
        assertThatThrownBy(()->K8sObjectStorageTunnelRuntime.publicKey("ssh-rsa abc")).isInstanceOf(IllegalArgumentException.class);
        assertThat(K8sObjectStorageTunnelRuntime.publicKey("ssh-ed25519 YWJj comment")).isEqualTo("ssh-ed25519 YWJj");
    }
    @Test void generatedCredentialsAreUniqueAndDoNotIncludeGatewayToken() {
        var runtime=new K8sObjectStorageTunnelRuntime(null,null);
        var first=runtime.credentials("default","mcmp-jupyter-1");
        var second=runtime.credentials("default","mcmp-jupyter-2");
        assertThat(first.getImmutable()).isTrue();
        assertThat(first.getStringData()).containsOnlyKeys("client-key","host-key","host-public","authorized_keys");
        assertThat(first.getStringData().get("client-key")).isNotEqualTo(second.getStringData().get("client-key"));
        assertThat(first.getStringData().get("authorized_keys")).startsWith("restrict,port-forwarding,permitlisten=\"127.0.0.1:18084\" ssh-ed25519 ");
    }
}
