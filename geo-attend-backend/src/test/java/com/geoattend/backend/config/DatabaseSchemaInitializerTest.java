package com.geoattend.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DatabaseSchemaInitializerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private DatabaseMetaData metaData;

    @Mock
    private ResultSet tablesResultSet;

    @Mock
    private ResultSet columnsResultSet;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    @DisplayName("When disabled, performs zero database operations, queries, DDL, or DML")
    void whenDisabled_doesNotPerformAnyDatabaseOperations() throws Exception {
        DatabaseSchemaInitializer initializer = new DatabaseSchemaInitializer(jdbcTemplate, false);

        assertFalse(initializer.isEnabled(), "Initializer should report disabled");

        initializer.run(applicationArguments);

        // Verify absolutely no interaction with JdbcTemplate or DataSource
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(dataSource);
        verifyNoInteractions(connection);
        verifyNoInteractions(metaData);
    }

    @Test
    @DisplayName("When disabled, running with null ApplicationArguments is safe and performs no DB calls")
    void whenDisabled_runWithNullArguments_safeAndNoDbInteractions() {
        DatabaseSchemaInitializer initializer = new DatabaseSchemaInitializer(jdbcTemplate, false);

        assertDoesNotThrow(() -> initializer.run(null));

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("When enabled, connects to database and executes schema inspection")
    void whenEnabled_executesSchemaChecks() throws Exception {
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        // Simulate that tables already exist
        when(metaData.getTables(any(), any(), anyString(), any())).thenReturn(tablesResultSet);
        when(tablesResultSet.next()).thenReturn(true);

        // Simulate that columns already exist
        when(metaData.getColumns(any(), any(), anyString(), anyString())).thenReturn(columnsResultSet);
        when(columnsResultSet.next()).thenReturn(true);

        // Simulate non-empty batches table (e.g. 3 records)
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM batches"), eq(Integer.class))).thenReturn(3);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM google_signups"), eq(Integer.class))).thenReturn(5);

        DatabaseSchemaInitializer initializer = new DatabaseSchemaInitializer(jdbcTemplate, true);

        assertTrue(initializer.isEnabled(), "Initializer should report enabled");

        initializer.run(applicationArguments);

        // Verify datasource was accessed and inspected
        verify(jdbcTemplate, atLeastOnce()).getDataSource();
        verify(dataSource, atLeastOnce()).getConnection();
        verify(metaData, atLeastOnce()).getDatabaseProductName();
    }

    @Test
    @DisplayName("When enabled and batches table is empty, seeds initial cohorts")
    void whenEnabled_seedsInitialBatchesIfEmpty() throws Exception {
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        when(metaData.getTables(any(), any(), anyString(), any())).thenReturn(tablesResultSet);
        when(tablesResultSet.next()).thenReturn(true);

        when(metaData.getColumns(any(), any(), anyString(), anyString())).thenReturn(columnsResultSet);
        when(columnsResultSet.next()).thenReturn(true);

        // Batches table exists but row count is 0
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM batches"), eq(Integer.class))).thenReturn(0);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM google_signups"), eq(Integer.class))).thenReturn(1);

        DatabaseSchemaInitializer initializer = new DatabaseSchemaInitializer(jdbcTemplate, true);

        initializer.run(applicationArguments);

        // Verify seeding occurred
        verify(jdbcTemplate).update("INSERT INTO batches (name, status) VALUES (?, ?)", "5th Generation", "ACTIVE");
        verify(jdbcTemplate).update("INSERT INTO batches (name, status) VALUES (?, ?)", "6th Generation", "ACTIVE");
        verify(jdbcTemplate).update("INSERT INTO batches (name, status) VALUES (?, ?)", "7th Generation", "ACTIVE");
    }

    @Test
    @DisplayName("When enabled and member_signups table is missing on PostgreSQL, creates it with BIGSERIAL")
    void whenEnabled_createsMemberSignupsTable_Postgres() throws Exception {
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");

        // Return false for doesTableExist("member_signups") and true for others
        when(metaData.getTables(any(), any(), anyString(), any())).thenAnswer(invocation -> {
            String table = invocation.getArgument(2);
            ResultSet rs = mock(ResultSet.class);
            if ("member_signups".equalsIgnoreCase(table)) {
                when(rs.next()).thenReturn(false);
            } else {
                when(rs.next()).thenReturn(true);
            }
            return rs;
        });

        // Columns exist for members
        when(metaData.getColumns(any(), any(), anyString(), anyString())).thenReturn(columnsResultSet);
        when(columnsResultSet.next()).thenReturn(true);

        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM batches"), eq(Integer.class))).thenReturn(3);

        DatabaseSchemaInitializer initializer = new DatabaseSchemaInitializer(jdbcTemplate, true);
        initializer.run(applicationArguments);

        // Verify PostgreSQL table DDL executed
        verify(jdbcTemplate).execute(contains("BIGSERIAL PRIMARY KEY"));
        verify(jdbcTemplate).execute(contains("idx_member_signups_status"));
    }

    @Test
    @DisplayName("When enabled and obsolete google_signups is empty, drops it")
    void whenEnabled_dropsEmptyGoogleSignupsTable() throws Exception {
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("MySQL");

        when(metaData.getTables(any(), any(), anyString(), any())).thenReturn(tablesResultSet);
        when(tablesResultSet.next()).thenReturn(true);

        when(metaData.getColumns(any(), any(), anyString(), anyString())).thenReturn(columnsResultSet);
        when(columnsResultSet.next()).thenReturn(true);

        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM google_signups"), eq(Integer.class))).thenReturn(0);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM batches"), eq(Integer.class))).thenReturn(3);

        DatabaseSchemaInitializer initializer = new DatabaseSchemaInitializer(jdbcTemplate, true);
        initializer.run(applicationArguments);

        verify(jdbcTemplate).execute("DROP TABLE google_signups");
    }
}
