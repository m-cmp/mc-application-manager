package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import kr.co.mcmp.softwarecatalog.application.constants.ArtifactSource;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.service.NexusIntegrationService;
import kr.co.mcmp.util.Base64Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Downloads imported charts without persisting Registry credentials in Helm's global config. */
@Service
@RequiredArgsConstructor
public class NexusHelmChartAccess {
    private final NexusIntegrationService nexus;

    public Path download(HelmChart chart) throws IOException, InterruptedException {
        if (chart.getArtifactSource() != ArtifactSource.NEXUS)
            throw new IllegalArgumentException("A verified Nexus chart is required");
        var info = nexus.getNexusInfoFromDB();
        String repository = nexus.getRepositoryNameByFormat("helm");
        String expected = info.getOssUrl().replaceAll("/+$", "") + "/repository/" + repository;
        String stored = chart.getChartRepositoryUrl();
        if (stored == null || !expected.equals(stored.replaceAll("/+$", "")))
            throw new IllegalArgumentException("Refusing to send Nexus credentials to another chart repository");
        URI base = URI.create(expected);
        if (base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !("http".equals(base.getScheme()) || "https".equals(base.getScheme())))
            throw new IllegalArgumentException("Invalid Nexus chart repository URL");
        if (chart.getChartName() == null || !chart.getChartName().matches("[a-z0-9][a-z0-9.-]{0,127}")
                || chart.getChartVersion() == null || !chart.getChartVersion().matches("[A-Za-z0-9][A-Za-z0-9.+_-]{0,127}"))
            throw new IllegalArgumentException("Invalid imported Helm chart name or version");
        String password = Base64Utils.base64Decoding(info.getOssPassword());
        if (info.getOssUsername() == null || info.getOssUsername().isBlank() || password == null || password.isEmpty())
            throw new IllegalStateException("Nexus chart credentials are unavailable");
        String token = Base64.getEncoder().encodeToString((info.getOssUsername() + ":" + password).getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create(expected + "/" + chart.getChartName() + "-" + chart.getChartVersion() + ".tgz"))
                .timeout(Duration.ofSeconds(120)).header("Authorization", "Basic " + token).GET().build();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        Path archive = Files.createTempFile("am-nexus-chart-", ".tgz");
        boolean downloaded = false;
        try {
            var response = client.send(request, HttpResponse.BodyHandlers.ofFile(archive));
            if (response.statusCode() != 200)
                throw new IOException("Nexus chart download failed (HTTP " + response.statusCode() + ")");
            try (var input = Files.newInputStream(archive)) {
                if (input.read() != 0x1f || input.read() != 0x8b)
                    throw new IOException("Nexus did not return a Helm chart archive");
            }
            downloaded = true;
            return archive;
        } finally {
            if (!downloaded) Files.deleteIfExists(archive);
        }
    }
}
