package kr.co.mcmp.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

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
        ensureBuiltInCatalogCapabilities();
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
            ensureStorageClassCapability("rclone");
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
