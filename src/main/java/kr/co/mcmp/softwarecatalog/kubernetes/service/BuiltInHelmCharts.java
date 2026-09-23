package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.*;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;
import kr.co.mcmp.softwarecatalog.application.model.HelmChart;

/** Versioned, dependency-free Helm charts shipped in the AM image (not application images). */
public final class BuiltInHelmCharts {
    public static final String REPOSITORY = "mcmp-builtin";
    public static final String URL = "classpath:helm";
    public static final String VERSION = "0.1.0";
    public record App(String chart, String title, int port, boolean persistent, String image, String appVersion) { }
    public static final List<App> APPS = List.of(
            new App("apache", "Apache HTTP Server", 80, false, "httpd", "2.4-alpine"),
            new App("tomcat", "Apache Tomcat", 8080, false, "tomcat", "10.1-jre17-temurin"),
            new App("redis", "Redis", 6379, true, "redis", "7.4-alpine"),
            new App("mariadb", "MariaDB", 3306, true, "mariadb", "11.4"),
            new App("postgresql", "PostgreSQL", 5432, true, "postgres", "17-alpine"));
    private static final List<String> TEMPLATES = List.of("workload.yaml", "service.yaml", "ingress.yaml",
            "pvc.yaml", "secret.yaml", "config.yaml", "hpa.yaml");
    private BuiltInHelmCharts() { }

    public static Optional<App> app(HelmChart chart) {
        if (chart == null || !REPOSITORY.equals(chart.getRepositoryName()) || !URL.equals(chart.getChartRepositoryUrl())
                || !VERSION.equals(chart.getChartVersion())) return Optional.empty();
        return APPS.stream().filter(a -> a.chart().equals(chart.getChartName())
                && (REPOSITORY + "-" + a.chart()).equals(chart.getPackageId())).findFirst();
    }

    /** Caller owns and must delete the returned temporary archive, even on Helm failure. */
    public static Path packageChart(App app) throws IOException {
        if (!APPS.contains(app)) throw new IllegalArgumentException("Unknown built-in chart");
        Path output = Files.createTempFile("am-chart-" + app.chart() + "-", ".tgz");
        try (var tar = new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(output)))) {
            entry(tar, app.chart() + "/Chart.yaml", "apiVersion: v2\nname: " + app.chart()
                    + "\ntype: application\nversion: " + VERSION + "\nappVersion: \"" + app.appVersion() + "\"\n");
            Map<String, Object> values = new Yaml().load(read("common/values.yaml"));
            Map<String, Object> specific = new Yaml().load(read(app.chart() + "/values.yaml"));
            values.putAll(specific);
            entry(tar, app.chart() + "/values.yaml", new Yaml().dump(values));
            for (String file : TEMPLATES) entry(tar, app.chart() + "/templates/" + file, read("common/templates/" + file));
        } catch (Exception e) {
            Files.deleteIfExists(output);
            throw e;
        }
        return output;
    }
    private static String read(String path) throws IOException {
        try (InputStream input = new ClassPathResource("helm/" + path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    private static void entry(TarArchiveOutputStream tar, String path, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry entry = new TarArchiveEntry(path);
        entry.setSize(bytes.length);
        entry.setModTime(0);
        entry.setMode(0644);
        tar.putArchiveEntry(entry);
        tar.write(bytes);
        tar.closeArchiveEntry();
    }
}
