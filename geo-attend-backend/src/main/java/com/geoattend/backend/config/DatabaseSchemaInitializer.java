package com.geoattend.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking database schema for batch management and registration fields...");

        ensureColumn("members", "batch_id", "BIGINT NULL");
        ensureColumn("members", "student_id", "VARCHAR(50) NULL");

        ensureTable("""
            CREATE TABLE IF NOT EXISTS member_signups (
                id BIGINT NOT NULL AUTO_INCREMENT,
                name VARCHAR(255) NOT NULL,
                email VARCHAR(320) NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                requested_batch_id BIGINT NULL,
                student_id VARCHAR(50) NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                rejection_reason VARCHAR(500) NULL,
                reviewed_by BIGINT NULL,
                reviewed_at TIMESTAMP NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id),
                UNIQUE KEY uq_member_signups_email (email),
                KEY idx_member_signups_status (status)
            )
        """);

        // Safely clean up obsolete empty google_signups table if present
        try {
            String checkSql = """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name = 'google_signups'
            """;
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class);
            if (count != null && count > 0) {
                Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM google_signups", Integer.class);
                if (rows != null && rows == 0) {
                    log.info("Cleaning up obsolete empty google_signups table...");
                    jdbcTemplate.execute("DROP TABLE google_signups");
                }
            }
        } catch (Exception e) {
            log.warn("Could not check or clean up obsolete google_signups table: {}", e.getMessage());
        }

        ensureColumn("attendance_location_audit", "accuracy_status", "VARCHAR(30) NULL DEFAULT 'HIGH'");
        ensureColumn("attendance_location_audit", "boundary_tolerance_meters", "DECIMAL(10,2) NULL");
        ensureColumn("attendance_location_audit", "evaluation_reason", "VARCHAR(255) NULL");

        // Seed initial batches if empty
        try {
            Integer batchCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM batches", Integer.class);
            if (batchCount != null && batchCount == 0) {
                log.info("Seeding initial batches: 5th Generation, 6th Generation, 7th Generation");
                jdbcTemplate.update("INSERT INTO batches (name, status) VALUES (?, ?)", "5th Generation", "ACTIVE");
                jdbcTemplate.update("INSERT INTO batches (name, status) VALUES (?, ?)", "6th Generation", "ACTIVE");
                jdbcTemplate.update("INSERT INTO batches (name, status) VALUES (?, ?)", "7th Generation", "ACTIVE");
            }
        } catch (Exception e) {
            log.warn("Could not check or seed batches: {}", e.getMessage());
        }
    }

    private void ensureTable(String createTableSql) {
        try {
            jdbcTemplate.execute(createTableSql);
        } catch (Exception e) {
            log.warn("Could not ensure table: {}", e.getMessage());
        }
    }

    private void ensureColumn(String tableName, String columnName, String columnDefinition) {
        try {
            String checkSql = """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
            """;
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, tableName, columnName);
            if (count != null && count == 0) {
                log.info("Adding missing column {} to table {}", columnName, tableName);
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
            }
        } catch (Exception e) {
            log.warn("Could not ensure column {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }
}
