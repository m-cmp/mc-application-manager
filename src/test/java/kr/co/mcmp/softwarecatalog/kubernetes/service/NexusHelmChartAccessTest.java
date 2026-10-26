package kr.co.mcmp.softwarecatalog.kubernetes.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.softwarecatalog.application.constants.ArtifactSource;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NexusHelmChartAccessTest {
    private HttpServer server;
    private NexusHelmChartAccess access;
    private HelmChart chart;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        String origin = "http://127.0.0.1:" + server.getAddress().getPort();
        var nexus = mock(NexusIntegrationService.class);
        when(nexus.getNexusInfoFromDB()).thenReturn(OssDto.builder().ossUrl(origin).ossUsername("registry-user")
                .ossPassword(Base64.getEncoder().encodeToString("test-password".getBytes())).build());
        when(nexus.getRepositoryNameByFormat("helm")).thenReturn("helm-hosted");
        access = new NexusHelmChartAccess(nexus);
        chart = HelmChart.builder().chartName("podinfo").chartVersion("6.15.0")
                .chartRepositoryUrl(origin + "/repository/helm-hosted").artifactSource(ArtifactSource.NEXUS).build();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void downloadsTheExactPrivateNexusChartUsingAuthentication() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) { gzip.write("chart archive".getBytes()); }
        var authorization = new AtomicReference<String>();
        server.createContext("/repository/helm-hosted/podinfo-6.15.0.tgz", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, bytes.size());
            exchange.getResponseBody().write(bytes.toByteArray());
            exchange.close();
        });
        var file = access.download(chart);
        try {
            assertThat(Files.readAllBytes(file)).isEqualTo(bytes.toByteArray());
            assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder()
                    .encodeToString("registry-user:test-password".getBytes()));
        } finally { Files.deleteIfExists(file); }
    }

    @Test void refusesAnUntrustedStoredRepositoryBeforeSendingCredentials() {
        chart.setChartRepositoryUrl("https://untrusted.example/repository/helm-hosted");
        assertThatThrownBy(() -> access.download(chart)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another chart repository");
    }

    @Test void rejectsRedirectsAndDoesNotForwardNexusCredentials() {
        var redirectedRequests = new AtomicInteger();
        server.createContext("/repository/helm-hosted/podinfo-6.15.0.tgz", exchange -> {
            exchange.getResponseHeaders().set("Location", "/unexpected");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/unexpected", exchange -> {
            redirectedRequests.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close();
        });
        assertThatThrownBy(() -> access.download(chart)).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("HTTP 302");
        assertThat(redirectedRequests).hasValue(0);
    }

    @Test void doesNotAcceptAnErrorPageAsAChart() {
        server.createContext("/repository/helm-hosted/podinfo-6.15.0.tgz", exchange -> {
            byte[] body = "<html>error</html>".getBytes();
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        assertThatThrownBy(() -> access.download(chart)).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("chart archive");
    }
}
