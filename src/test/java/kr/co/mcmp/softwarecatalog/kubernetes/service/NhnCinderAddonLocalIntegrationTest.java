package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.*;
import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.exception.GlobalExceptionHandler;
import kr.co.mcmp.security.project.ProjectScopeAuthorizationService;
import kr.co.mcmp.softwarecatalog.application.controller.*;
import kr.co.mcmp.softwarecatalog.kubernetes.config.KubernetesClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Real loopback HTTP for OpenBao/Identity/NKS and Fabric8; no credentials or requests leave this machine. */
class NhnCinderAddonLocalIntegrationTest {
    @TempDir Path directory;
    record Request(String method, String path, String query, String body, Map<String, List<String>> headers) { }

    @Test void localRestJobInstallsCompatibleCinderWaitsForWorkersAndCreatesJupyterStorageClass() throws Exception {
        var json = new ObjectMapper();
        var kube = new KubernetesServer(false, true); kube.before();
        var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        var requests = new CopyOnWriteArrayList<Request>();
        var handlerFailure = new AtomicReference<Throwable>();
        var installed = new AtomicBoolean(); var addonPolls = new AtomicInteger();
        final String id = NhnCinderAddonServiceTest.ID;
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            requests.add(new Request(method, path, exchange.getRequestURI().getRawQuery(), body, Map.copyOf(exchange.getRequestHeaders())));
            int status = 200; String response;
            try {
                if (path.equals("/v1/secret/data/csp/nhn")) {
                    assertThat(exchange.getRequestHeaders().getFirst("X-Vault-Token")).isEqualTo("fake-vault-read-token");
                    response = "{\"data\":{\"data\":{\"NHN_IDENTITY_ENDPOINT\":\"https://api-identity-infrastructure.nhncloudservice.com/v2.0\",\"NHN_USERNAME\":\"fake-user\",\"NHN_PASSWORD\":\"fake-password\",\"NHN_TENANT_ID\":\"tenant-a\"}}}";
                } else if (path.equals("/v2.0/tokens")) {
                    assertThat(json.readTree(body).path("auth").path("tenantId").asText()).isEqualTo("tenant-a");
                    response = NhnTestHttp.loginBody("tenant-a", "https://kr1-api-kubernetes-infrastructure.nhncloudservice.com/v1/");
                } else {
                    assertThat(exchange.getRequestHeaders().getFirst("X-Auth-Token")).isEqualTo("fake-nhn-token");
                    assertThat(exchange.getRequestHeaders().getFirst("OpenStack-API-Version")).isEqualTo("container-infra latest");
                    if (path.equals("/v1/clusters/" + id)) {
                        response = "{\"uuid\":\"" + id + "\",\"project_id\":\"tenant-a\",\"status\":\"CREATE_COMPLETE\",\"coe_version\":\"v1.30.0\",\"labels\":{\"node_image\":\"01234567-89ab-cdef-0123-456789abcdef\",\"platform_version\":\"1.202605.0\"}}";
                    } else if (path.equals("/v1/addons/")) {
                        assertThat(exchange.getRequestURI().getQuery()).contains("k8s_version=v1.30.0", "platform_version=1.202605.0", "image=01234567");
                        response = "{\"addons\":[{\"name\":\"cinder_csi_plugin\",\"version\":\"v1.9.0-nks1\"},{\"name\":\"cinder_csi_plugin\",\"version\":\"v1.10.0-nks2\"}]}";
                    } else if (path.equals("/v1/clusters/" + id + "/addons") && method.equals("POST")) {
                        assertThat(json.readTree(body).path("name").asText()).isEqualTo("cinder_csi_plugin");
                        assertThat(json.readTree(body).path("resolve_conflicts").asText()).isEqualTo("none");
                        assertThat(json.readTree(body).path("version").asText()).isEqualTo("v1.10.0-nks2");
                        assertThat(installed.compareAndSet(false, true)).isTrue();
                        response = "{\"uuid\":\"" + id + "\"}";
                    } else if (path.equals("/v1/clusters/" + id + "/addons")) {
                        if (!installed.get()) response = "{\"addons\":[{\"name\":\"cinder-csi-plugin\",\"cluster_uuid\":\"" + id + "\",\"project_id\":\"tenant-a\",\"version\":null,\"status\":\"NOT_INSTALLED\"}]}";
                        else {
                            int poll = addonPolls.incrementAndGet();
                            if (poll == 2) NhnCinderAddonServiceTest.registerDriver(kube.getClient());
                            String state = poll < 2 ? "CREATE_IN_PROGRESS" : "CREATE_COMPLETE";
                            response = "{\"addons\":[{\"name\":\"cinder-csi-plugin\",\"cluster_uuid\":\"" + id + "\",\"project_id\":\"tenant-a\",\"version\":\"v1.10.0-nks2\",\"status\":\"" + state + "\"}]}";
                        }
                    } else throw new AssertionError("Unexpected loopback request " + path);
                }
            } catch (Throwable t) { handlerFailure.set(t); status = 500; response = "{}"; }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var jobsRef = new AtomicReference<NhnCinderAddonJobs>();
        try {
            URI loopback = URI.create("http://localhost:" + server.getAddress().getPort());
            var properties = NhnCinderAddonServiceTest.configured(); properties.setReadyTimeoutSeconds(5); properties.setOpenBaoUrl(loopback.toString());
            var token = directory.resolve("vault-token"); Files.writeString(token, "fake-vault-read-token"); properties.setOpenBaoTokenFile(token.toString());
            var nativeHttp = mock(HttpClient.class);
            var realHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
            // Only the test transport changes the destination; production still validates official HTTPS endpoints.
            when(nativeHttp.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenAnswer(a -> {
                HttpRequest source = a.getArgument(0);
                String suffix = source.uri().getRawPath() + (source.uri().getRawQuery() == null ? "" : "?" + source.uri().getRawQuery());
                var request = HttpRequest.newBuilder(loopback.resolve(suffix)).timeout(Duration.ofSeconds(3));
                source.headers().map().forEach((key, values) -> values.forEach(value -> request.header(key, value)));
                return realHttp.send(request.method(source.method(), source.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())).build(), HttpResponse.BodyHandlers.ofString());
            });
            var tumblebug = mock(CbtumblebugRestApi.class);
            when(tumblebug.getK8sClusterByName("project-a", "cluster-a")).thenReturn(NhnCinderAddonServiceTest.cluster("nhn"));
            var clients = mock(KubernetesClientFactory.class);
            when(clients.getClient("project-a", "cluster-a")).thenAnswer(a -> new KubernetesClientBuilder().withConfig(kube.getClient().getConfiguration()).build());
            var service = new NhnCinderAddonService(tumblebug, clients, properties, new NhnOpenBaoCredentials(properties), new NhnNksApi(nativeHttp));
            var jobs = new NhnCinderAddonJobs(service); jobsRef.set(jobs);
            var authorization = mock(ProjectScopeAuthorizationService.class);
            var storage = new NhnStorageClassService(clients, tumblebug);
            var mvc = MockMvcBuilders.standaloneSetup(new NhnCinderAddonController(authorization, service, jobs),
                    new ApplicationController(null, null, null, null, new KubernetesStorageClassService(clients), storage, authorization, null))
                    .setControllerAdvice(new GlobalExceptionHandler()).build();
            String response = mvc.perform(post("/applications/k8s/nhn-cinder-addon").param("namespace", "project-a").param("clusterName", "cluster-a"))
                    .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            String jobId = json.readTree(response).path("data").path("id").asText();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                String state = jobs.get("project-a", "cluster-a", jobId).state();
                if (state.equals("READY") || state.equals("FAILED")) break;
                Thread.sleep(20);
            }
            assertThat(handlerFailure.get()).isNull();
            mvc.perform(get("/applications/k8s/nhn-cinder-addon/jobs/" + jobId).param("namespace", "project-a").param("clusterName", "cluster-a"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("READY"));
            assertThat(requests.stream().filter(r -> r.method().equals("POST") && r.path().endsWith("/addons"))).hasSize(1);
            int nativeCount = requests.size();
            mvc.perform(post("/applications/k8s/nhn-cinder-addon").param("namespace", "project-a").param("clusterName", "cluster-a"))
                    .andExpect(status().isAccepted());
            verifyNoMoreCloudAfterReuse(service, requests, nativeCount);

            kube.expect().post().withPath("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")
                    .andReturn(201, new SelfSubjectAccessReviewBuilder().withNewStatus().withAllowed(true).endStatus().build()).once();
            mvc.perform(post("/applications/k8s/storage-classes/nhn").param("namespace", "project-a").param("clusterName", "cluster-a")
                    .contentType("application/json").content("{\"name\":\"am-local-notebooks\",\"diskType\":\"General HDD\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("am-local-notebooks"));
            var storageClass = JupyterStorageValidation.validate(kube.getClient(), Map.of("storageClass", "am-local-notebooks", "storageSize", "10Gi"));
            assertThat(storageClass.getProvisioner()).isEqualTo("cinder.csi.openstack.org");
            assertThat(storageClass.getParameters()).containsEntry("csi.storage.k8s.io/fstype", "ext4");
            assertThat(storageClass.getVolumeBindingMode()).isEqualTo("WaitForFirstConsumer");
            assertThat(storageClass.getReclaimPolicy()).isEqualTo("Retain");
        } finally {
            if (jobsRef.get() != null) jobsRef.get().shutdown(); server.stop(0); kube.after();
        }
    }
    private static void verifyNoMoreCloudAfterReuse(NhnCinderAddonService service, List<Request> requests, int count) {
        service.prepare(service.resolve("project-a", "cluster-a"), m -> {});
        assertThat(requests).hasSize(count);
    }
}
