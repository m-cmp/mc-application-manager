package kr.co.mcmp.util;

import java.nio.charset.StandardCharsets;
import java.util.List;
import kr.co.mcmp.softwarecatalog.kubernetes.service.BuiltInHelmCharts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class DatabaseInitializer implements CommandLineRunner{

    private static final String STORAGE_CLASS_CAPABILITY = "storage-class";
    private static final String JUPYTER_CATALOG_TITLE = "JupyterLab for Object Storage";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ResourceLoader resourceLoader;

    @Override
    public void run(String... args) throws Exception {
        // SOFTWARE_SOURCE_MAPPING 테이블이 비어있을 때만 실행 (이 테이블이 마지막에 생성되므로)
        if (isSourceMappingEmpty() && isDatabaseEmpty()) {
            System.out.println("데이터베이스 초기화를 시작합니다...");
            Resource resource = resourceLoader.getResource("classpath:import.sql");
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            jdbcTemplate.execute(sql);
            System.out.println("데이터베이스 초기화가 완료되었습니다.");
        } else {
            System.out.println("데이터베이스가 이미 초기화되어 있습니다. 건너뜁니다.");
        }
        ensureBuiltInJupyterCatalog();
        ensureNginxHelmCatalog();
        ensureBuiltInHelmCatalogs();
        ensureBuiltInCatalogCapabilities();
    }

    private void ensureBuiltInHelmCatalogs() {
        for (var app : BuiltInHelmCharts.APPS) {
            // Reuse the existing VM catalog by title, without changing its settings or Docker image.
            jdbcTemplate.update("""
                    INSERT INTO SOFTWARE_CATALOG (
                        TITLE, DESCRIPTION, SUMMARY, CATEGORY, MIN_CPU, RECOMMENDED_CPU, MIN_MEMORY,
                        RECOMMENDED_MEMORY, MIN_DISK, RECOMMENDED_DISK, CPU_THRESHOLD, MEMORY_THRESHOLD,
                        MIN_REPLICAS, MAX_REPLICAS, HPA_ENABLED, DEFAULT_PORT, INGRESS_ENABLED, CREATED_AT, UPDATED_AT)
                    SELECT ?, ?, ?, ?, 0.1, 1, 0.125, 1, 1, 10, 80, 80, 1, 1, false, ?, false,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    WHERE NOT EXISTS (SELECT 1 FROM SOFTWARE_CATALOG WHERE LOWER(TITLE) = LOWER(?))
                    """, app.title(), app.title() + " deployed with the AM bundled Helm chart.", app.title(),
                    app.persistent() ? "Databases & Storage" : "Web Servers", app.port(), app.title());
            for (Long id : jdbcTemplate.queryForList("SELECT ID FROM SOFTWARE_CATALOG WHERE LOWER(TITLE) = LOWER(?)",
                    Long.class, app.title())) {
                jdbcTemplate.update("""
                        INSERT INTO HELM_CHART (CATALOG_ID, CHART_NAME, CHART_VERSION, CHART_REPOSITORY_URL,
                            REPOSITORY_NAME, REPOSITORY_DISPLAY_NAME, REPOSITORY_OFFICIAL, PACKAGE_ID,
                            NORMALIZED_NAME, APP_VERSION, DESCRIPTION, CATEGORY, IMAGE_REPOSITORY, TAG, HAS_VALUES_SCHEMA)
                        SELECT ?, ?, ?, ?, ?, 'MCMP bundled charts', false, ?, ?, ?, ?, ?, ?, ?, false
                        WHERE NOT EXISTS (SELECT 1 FROM HELM_CHART WHERE CATALOG_ID = ?)
                        """, id, app.chart(), BuiltInHelmCharts.VERSION, BuiltInHelmCharts.URL, BuiltInHelmCharts.REPOSITORY,
                        BuiltInHelmCharts.REPOSITORY + "-" + app.chart(), app.chart(), app.appVersion(),
                        "Single-instance " + app.title() + " using a digest-pinned official container image.",
                        app.persistent() ? "Databases & Storage" : "Web Servers", app.image(), app.appVersion(), id);
                // A user-provided Helm mapping wins; do not attach our capability to a different chart.
                Integer managed = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM HELM_CHART WHERE CATALOG_ID = ? AND PACKAGE_ID = ?
                        AND CHART_REPOSITORY_URL = ? AND CHART_VERSION = ? AND REPOSITORY_NAME = ? AND CHART_NAME = ?
                        """, Integer.class, id, BuiltInHelmCharts.REPOSITORY + "-" + app.chart(), BuiltInHelmCharts.URL,
                        BuiltInHelmCharts.VERSION, BuiltInHelmCharts.REPOSITORY, app.chart());
                if (managed != null && managed > 0) {
                    ensureCatalogReference(id, "helm_application_install", "workflow");
                    ensureCatalogReference(id, "helm_application_uninstall", "workflow");
                    if (app.persistent()) {
                        ensureCatalogReference(id, STORAGE_CLASS_CAPABILITY, "CAPABILITY");
                        ensureCatalogReference(id, "persistent-single-instance", "CAPABILITY");
                    }
                }
            }
        }
    }

    private void ensureNginxHelmCatalog() {
        jdbcTemplate.update("""
                INSERT INTO SOFTWARE_CATALOG (
                    TITLE, DESCRIPTION, SUMMARY, CATEGORY, LOGO_URL_LARGE, LOGO_URL_SMALL,
                    MIN_CPU, RECOMMENDED_CPU, MIN_MEMORY, RECOMMENDED_MEMORY, MIN_DISK,
                    RECOMMENDED_DISK, CPU_THRESHOLD, MEMORY_THRESHOLD, MIN_REPLICAS,
                    MAX_REPLICAS, HPA_ENABLED, DEFAULT_PORT, INGRESS_ENABLED, CREATED_AT, UPDATED_AT)
                SELECT 'Nginx for Kubernetes', 'General-purpose Nginx web server deployed with Helm on Kubernetes.',
                    'Nginx web server', 'Web Server', '/catalog-icons/nginx.svg', '/catalog-icons/nginx.svg',
                    0.1, 0.5, 0.0625, 0.25, 1, 1, 80.0, 80.0, 1, 1, false, 80, true,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM SOFTWARE_CATALOG WHERE LOWER(TITLE) = 'nginx for kubernetes')
                """);
        for (Long catalogId : jdbcTemplate.queryForList(
                "SELECT ID FROM SOFTWARE_CATALOG WHERE LOWER(TITLE) = 'nginx for kubernetes'", Long.class)) {
            jdbcTemplate.update("""
                    INSERT INTO HELM_CHART (CATALOG_ID, CHART_NAME, CHART_VERSION, CHART_REPOSITORY_URL,
                        REPOSITORY_NAME, REPOSITORY_DISPLAY_NAME, REPOSITORY_OFFICIAL, PACKAGE_ID,
                        NORMALIZED_NAME, APP_VERSION, DESCRIPTION, CATEGORY, IMAGE_REPOSITORY, TAG, HAS_VALUES_SCHEMA)
                    SELECT ?, 'nginx', '0.16.8', 'https://cloudpirates-io.github.io/helm-charts', 'cloudpirates',
                        'CloudPirates', false, 'cloudpirates-nginx', 'nginx', '1.31.5',
                        'Nginx web server from the CloudPirates Helm repository.', 'Web Server', 'nginx', '0.16.8', true
                    WHERE NOT EXISTS (SELECT 1 FROM HELM_CHART WHERE CATALOG_ID = ?)
                    """, catalogId, catalogId);
            // Migrate only the exact AM-provided legacy mapping, never a customized chart.
            jdbcTemplate.update("""
                    UPDATE HELM_CHART
                       SET CHART_VERSION = '0.16.8',
                           CHART_REPOSITORY_URL = 'https://cloudpirates-io.github.io/helm-charts',
                           REPOSITORY_NAME = 'cloudpirates', REPOSITORY_DISPLAY_NAME = 'CloudPirates',
                           REPOSITORY_OFFICIAL = false, PACKAGE_ID = 'cloudpirates-nginx',
                           NORMALIZED_NAME = 'nginx', APP_VERSION = '1.31.5',
                           DESCRIPTION = 'Nginx web server from the CloudPirates Helm repository.',
                           IMAGE_REPOSITORY = 'nginx', TAG = '0.16.8', HAS_VALUES_SCHEMA = true
                     WHERE CATALOG_ID = ? AND PACKAGE_ID = 'mcmp-builtin-nginx'
                       AND CHART_NAME = 'nginx' AND CHART_VERSION = '0.1.0'
                       AND CHART_REPOSITORY_URL = 'http://localhost:18084/charts'
                       AND REPOSITORY_NAME = 'mcmp-builtin'
                    """, catalogId);
            ensureCatalogReference(catalogId, "nginx", "TAG");
        }
    }

    private void ensureBuiltInJupyterCatalog() {
        try {
            jdbcTemplate.update("""
                    INSERT INTO SOFTWARE_CATALOG (
                        TITLE, DESCRIPTION, SUMMARY, CATEGORY, LOGO_URL_LARGE, LOGO_URL_SMALL,
                        MIN_CPU, RECOMMENDED_CPU, MIN_MEMORY, RECOMMENDED_MEMORY, MIN_DISK,
                        RECOMMENDED_DISK, CPU_THRESHOLD, MEMORY_THRESHOLD, MIN_REPLICAS,
                        MAX_REPLICAS, HPA_ENABLED, DEFAULT_PORT, INGRESS_ENABLED, CREATED_AT, UPDATED_AT)
                    SELECT ?,
                        'JupyterLab data analysis environment connected to Object Storage registered in Tumblebug.',
                        'Analyze Object Storage data in JupyterLab', 'Object Storage',
                        'https://raw.githubusercontent.com/jupyter/design/master/logos/Square%20Logo/squarelogo-greytext-orangebody-greymoons/squarelogo-greytext-orangebody-greymoons.png',
                        'https://raw.githubusercontent.com/jupyter/design/master/logos/Square%20Logo/squarelogo-greytext-orangebody-greymoons/squarelogo-greytext-orangebody-greymoons.png',
                        1, 2, 2, 4, 5, 10, 80.0, 80.0, 1, 1, false, 8888, false,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    WHERE NOT EXISTS (SELECT 1 FROM SOFTWARE_CATALOG WHERE LOWER(TITLE) = LOWER(?))
                    """, JUPYTER_CATALOG_TITLE, JUPYTER_CATALOG_TITLE);

            jdbcTemplate.update("""
                    UPDATE SOFTWARE_CATALOG
                       SET DESCRIPTION = 'JupyterLab data analysis environment connected to Object Storage registered in Tumblebug.',
                           UPDATED_AT = CURRENT_TIMESTAMP
                     WHERE LOWER(TITLE) = LOWER(?)
                       AND DESCRIPTION = 'JupyterLab data analysis environment connected to user-supplied S3-compatible object storage.'
                    """, JUPYTER_CATALOG_TITLE);

            List<Long> catalogIds = jdbcTemplate.queryForList(
                    "SELECT ID FROM SOFTWARE_CATALOG WHERE LOWER(TITLE) = LOWER(?)",
                    Long.class,
                    JUPYTER_CATALOG_TITLE);
            for (Long catalogId : catalogIds) {
                jdbcTemplate.update("""
                        INSERT INTO PACKAGE_INFO (
                            CATALOG_ID, PACKAGE_TYPE, PACKAGE_NAME, PACKAGE_VERSION, REPOSITORY_URL,
                            DOCKER_PUBLISHER, DOCKER_CREATED_AT, DOCKER_UPDATED_AT,
                            DOCKER_SHORT_DESCRIPTION, DOCKER_SOURCE, ARCHITECTURES, CATEGORIES,
                            IS_ARCHIVED, IS_AUTOMATED, IS_OFFICIAL, LAST_PULLED_AT, OPERATING_SYSTEMS)
                        SELECT ?, 'DOCKER', 'quay.io/jupyter/scipy-notebook', '2026-07-28',
                            'https://quay.io/repository/jupyter/scipy-notebook', 'Jupyter Docker Stacks',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                            'JupyterLab scientific Python environment for Object Storage analysis',
                            'official', 'amd64,arm64', 'Object Storage', false, false, true,
                            CURRENT_TIMESTAMP, 'linux'
                        WHERE NOT EXISTS (SELECT 1 FROM PACKAGE_INFO WHERE CATALOG_ID = ?)
                        """, catalogId, catalogId);

                ensureCatalogReference(catalogId, "https://jupyter-docker-stacks.readthedocs.io/", "HOMEPAGE");
                ensureCatalogReference(catalogId, "object-storage", "CAPABILITY");
                ensureCatalogReference(catalogId, "s3-compatible", "TAG");
                ensureCatalogReference(catalogId, "jupyterlab", "TAG");
                ensureCatalogReference(catalogId, "vm_application_install", "workflow");
                ensureCatalogReference(catalogId, "vm_application_uninstall", "workflow");
            }
        } catch (Exception e) {
            System.out.println("Built-in Jupyter catalog synchronization skipped: " + e.getMessage());
        }
    }

    private void ensureCatalogReference(Long catalogId, String value, String type) {
        Long existingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SOFTWARE_CATALOG_REF WHERE CATALOG_ID = ? AND LOWER(REF_VALUE) = LOWER(?) AND UPPER(REF_TYPE) = UPPER(?)",
                Long.class,
                catalogId,
                value,
                type);
        if (existingCount != null && existingCount > 0) {
            return;
        }

        Number nextRefIdx = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(REF_IDX), -1) + 1 FROM SOFTWARE_CATALOG_REF WHERE CATALOG_ID = ?",
                Number.class,
                catalogId);
        jdbcTemplate.update(
                "INSERT INTO SOFTWARE_CATALOG_REF (CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES (?, ?, ?, '', ?)",
                catalogId,
                nextRefIdx != null ? nextRefIdx.intValue() : 0,
                value,
                type);
    }

    private boolean isDatabaseEmpty() {
        try {
            // SOFTWARE_CATALOG 테이블이 존재하는지 확인
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SOFTWARE_CATALOG", Long.class);
            // 테이블이 존재하면 데이터 개수 확인
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SOFTWARE_CATALOG", Long.class);
            return count != null && count == 0;
        } catch (Exception e) {
            // 테이블이 존재하지 않으면 빈 데이터베이스로 간주
            return true;
        }
    }
    
    private boolean isSourceMappingEmpty() {
        try {
            // SOFTWARE_SOURCE_MAPPING 테이블이 존재하는지 확인
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SOFTWARE_SOURCE_MAPPING", Long.class);
            // 테이블이 존재하면 데이터 개수 확인
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SOFTWARE_SOURCE_MAPPING", Long.class);
            return count != null && count == 0;
        } catch (Exception e) {
            // 테이블이 존재하지 않으면 빈 데이터베이스로 간주
            return true;
        }
    }

    private void ensureBuiltInCatalogCapabilities() {
        try {
            ensureStorageClassCapability("loki");
        } catch (Exception e) {
            System.out.println("Built-in catalog capability synchronization skipped: " + e.getMessage());
        }
    }

    private void ensureStorageClassCapability(String chartName) {
        List<Long> catalogIds = jdbcTemplate.queryForList(
                "SELECT sc.ID FROM SOFTWARE_CATALOG sc JOIN HELM_CHART hc ON hc.CATALOG_ID = sc.ID WHERE LOWER(hc.CHART_NAME) = ?",
                Long.class,
                chartName.toLowerCase());

        for (Long catalogId : catalogIds) {
            Long existingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM SOFTWARE_CATALOG_REF WHERE CATALOG_ID = ? AND LOWER(REF_VALUE) = ? AND UPPER(REF_TYPE) IN ('CAPABILITY', 'TAG')",
                    Long.class,
                    catalogId,
                    STORAGE_CLASS_CAPABILITY);
            if (existingCount != null && existingCount > 0) {
                continue;
            }

            Number nextRefIdx = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(REF_IDX), -1) + 1 FROM SOFTWARE_CATALOG_REF WHERE CATALOG_ID = ?",
                    Number.class,
                    catalogId);

            jdbcTemplate.update(
                    "INSERT INTO SOFTWARE_CATALOG_REF (CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES (?, ?, ?, '', 'CAPABILITY')",
                    catalogId,
                    nextRefIdx != null ? nextRefIdx.intValue() : 0,
                    STORAGE_CLASS_CAPABILITY);
        }
    }
}
