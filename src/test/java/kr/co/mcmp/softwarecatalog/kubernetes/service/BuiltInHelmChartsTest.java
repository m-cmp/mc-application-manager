package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.*;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;
import kr.co.mcmp.softwarecatalog.application.dto.HelmChartDTO;

class BuiltInHelmChartsTest {
    static HelmChart chart(String name) {
        return HelmChart.builder().chartName(name).chartVersion(BuiltInHelmCharts.VERSION)
                .chartRepositoryUrl(BuiltInHelmCharts.URL).repositoryName(BuiltInHelmCharts.REPOSITORY)
                .packageId(BuiltInHelmCharts.REPOSITORY + "-" + name).build();
    }

    @Test void bundlesAllFiveWithPinnedImagesAndNoExternalChartDependencies() throws Exception {
        assertThat(BuiltInHelmCharts.APPS).hasSize(5);
        Path exports = Path.of("build", "builtin-charts");
        Files.createDirectories(exports);
        for (var app : BuiltInHelmCharts.APPS) {
            Path archive = BuiltInHelmCharts.packageChart(app);
            try {
                Map<String, String> entries = read(archive);
                assertThat(entries).hasSize(9);
                Map<String, Object> metadata = new Yaml().load(entries.get(app.chart() + "/Chart.yaml"));
                assertThat(metadata).containsEntry("name", app.chart()).doesNotContainKey("dependencies");
                Map<String, Object> values = new Yaml().load(entries.get(app.chart() + "/values.yaml"));
                assertThat(values.get("image").toString()).matches(".+@sha256:[a-f0-9]{64}");
                assertThat(((Map<?, ?>)values.get("persistence")).get("enabled")).isEqualTo(app.persistent());
                assertThat(BuiltInHelmCharts.app(chart(app.chart()))).contains(app);
                // Used by the explicit Helm lint/render and real-cluster test commands; these are production archives.
                Files.copy(archive, exports.resolve(app.chart() + "-0.1.0.tgz"), StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(archive); }
        }
    }
    @Test void doesNotInterceptUserOrExternalChartsOrUnknownVersions() {
        var custom = chart("redis"); custom.setChartRepositoryUrl("https://custom.example.org");
        assertThat(BuiltInHelmCharts.app(custom)).isEmpty();
        custom = chart("redis"); custom.setChartVersion("2.0.0");
        assertThat(BuiltInHelmCharts.app(custom)).isEmpty();
        custom = chart("redis"); custom.setPackageId("custom");
        assertThat(BuiltInHelmCharts.app(custom)).isEmpty();
        assertThat(BuiltInHelmCharts.app(chart("../secret"))).isEmpty();
        assertThat(BuiltInHelmCharts.app(null)).isEmpty();
    }
    @Test void catalogApiAndRoundTripPreserveTheIdentityNeededByTheInstallationForm() {
        var model = chart("redis");
        model.setNormalizedName("redis");
        var dto = HelmChartDTO.fromEntity(model);
        assertThat(dto.getPackageId()).isEqualTo("mcmp-builtin-redis");
        assertThat(dto.getNormalizedName()).isEqualTo("redis");
        assertThat(BuiltInHelmCharts.app(dto.toEntity())).isPresent();
    }
    private Map<String, String> read(Path archive) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (var tar = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(archive)))) {
            for (var entry = tar.getNextTarEntry(); entry != null; entry = tar.getNextTarEntry())
                entries.put(entry.getName(), new String(tar.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
        return entries;
    }
}
