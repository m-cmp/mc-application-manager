package kr.co.mcmp.util;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class DatabaseInitializer implements CommandLineRunner{

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ResourceLoader resourceLoader;

    @Override
    public void run(String... args) throws Exception {
        if (isDatabaseEmpty()) {
            Resource resource = resourceLoader.getResource("classpath:import.sql");
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            jdbcTemplate.execute(sql);
        }
    }

    private boolean isDatabaseEmpty() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM software_catalog", Long.class);
        return count != null && count == 0;
    }

}
