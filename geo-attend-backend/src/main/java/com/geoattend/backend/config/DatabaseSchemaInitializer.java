package com.geoattend.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public DatabaseSchemaInitializer(
            JdbcTemplate jdbcTemplate,
            @Value("${schema.initializer.enabled:false}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Database schema initializer is disabled (schema.initializer.enabled=false). Skipping all schema checks and modifications.");
            return;
        }

        log.info("Checking database schema for batch management and registration fields...");

        boolean isPostgres = isPostgreSql();

        ensureColumn("members", "batch_id", "BIGINT NULL");
        ensureColumn("members", "student_id", "VARCHAR(50) NULL");

        if (!doesTableExist("member_signups")) {
            if (isPostgres) {
                ensureTable("""
                    CREATE TABLE member_signups (
                        id BIGSERIAL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        email VARCHAR(320) NOT NULL UNIQUE,
                        password_hash VARCHAR(255) NOT NULL,
                        requested_batch_id BIGINT NULL,
                        student_id VARCHAR(50) NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        rejection_reason VARCHAR(500) NULL,
                        reviewed_by BIGINT NULL,
                        reviewed_at TIMESTAMP NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                """);
                ensureTable("CREATE INDEX IF NOT EXISTS idx_member_signups_status ON member_signups (status)");
            } else {
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
            }
        }

        // Safely clean up obsolete empty google_signups table if present
        try {
            if (doesTableExist("google_signups")) {
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
            if (doesTableExist("batches")) {
                Integer batchCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM batches", Integer.class);
                if (batchCount != null && batchCount == 0) {
                    log.info("Seeding initial batches: 5th Generation, 6th Generation, 7th Generation");
                    jdbcTemplate.update("INSERT INTO batches (name, status) VALUES (?, ?)", "5th Generation", "ACTIVE");
                    jdbcTemplate.update("INSERT INTO batches (name, status) VALUES (?, ?)", "6th Generation", "ACTIVE");
                    jdbcTemplate.update("INSERT INTO batches (name, status) VALUES (?, ?)", "7th Generation", "ACTIVE");
                }
            }
        } catch (Exception e) {
            log.warn("Could not check or seed batches: {}", e.getMessage());
        }
    }

    private boolean isPostgreSql() {
        DataSource ds = jdbcTemplate.getDataSource();
        if (ds == null) return false;
        try (Connection conn = ds.getConnection()) {
            String name = conn.getMetaData().getDatabaseProductName();
            return name != null && name.toLowerCase().contains("postgres");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean doesTableExist(String tableName) {
        DataSource ds = jdbcTemplate.getDataSource();
        if (ds == null) return false;
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = meta.getTables(null, null, tableName.toLowerCase(), new String[]{"TABLE"})) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
                if (rs.next()) return true;
            }
        } catch (Exception e) {
            log.warn("Could not inspect table metadata for {}: {}", tableName, e.getMessage());
        }
        return false;
    }

    private boolean doesColumnExist(String tableName, String columnName) {
        DataSource ds = jdbcTemplate.getDataSource();
        if (ds == null) return false;
        try (Connection conn = ds.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = meta.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = meta.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
                if (rs.next()) return true;
            }
        } catch (Exception e) {
            log.warn("Could not inspect column metadata for {}.{}: {}", tableName, columnName, e.getMessage());
        }
        return false;
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
            if (!doesTableExist(tableName)) {
                return;
            }
            if (!doesColumnExist(tableName, columnName)) {
                log.info("Adding missing column {} to table {}", columnName, tableName);
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
            }
        } catch (Exception e) {
            log.warn("Could not ensure column {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }
}
