package com.norcalretreat.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaFixConfig implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE retreat_registrations MODIFY COLUMN user_id BIGINT NULL");
            log.info("Schema fix applied: user_id is now nullable");
        } catch (Exception e) {
            log.warn("Schema fix skipped (may already be applied): {}", e.getMessage());
        }
    }
}
