package com.geoattend.backend.repository;

import com.geoattend.backend.model.AttendanceSession;
import com.geoattend.backend.model.AttendanceTracking;
import com.geoattend.backend.model.Batch;
import com.geoattend.backend.model.Member;
import com.geoattend.backend.model.MemberSignup;
import com.geoattend.backend.service.AttendanceTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PostgresRepositoryIntegrationTest {

    // Test safety mechanism: Mock the scheduled service so its @Scheduled method never fires on the DB
    @MockitoBean
    private AttendanceTrackingService attendanceTrackingService;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MemberSignupRepository memberSignupRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private AttendanceTrackingRepository trackingRepository;

    @Autowired
    private AttendanceSessionRepository sessionRepository;

    @Autowired
    private MemberRepository memberRepository;

    private boolean isDatasourceVerified = false;

    @BeforeEach
    void verifySafeDatasource() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();
            String productName = conn.getMetaData().getDatabaseProductName();

            if (!"PostgreSQL".equalsIgnoreCase(productName)) {
                throw new IllegalStateException(
                        "SAFETY ABORT: Test datasource is not PostgreSQL! Detected product: " + productName
                );
            }

            if ((!url.contains("5433") && !url.contains("5432")) || !url.contains("geoattend_test")) {
                throw new IllegalStateException(
                        "SAFETY ABORT: Test datasource does not target the disposable local PostgreSQL instance (port 5432/5433, db geoattend_test)! Target URL: " + url
                );
            }

            if (url.toLowerCase().contains("neon.tech") || url.toLowerCase().contains("render.com")) {
                throw new IllegalStateException(
                        "SAFETY ABORT: Cloud database host detected in test datasource URL! Target URL: " + url
                );
            }

            // Report effective PostgreSQL session timezone without exposing credentials
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SHOW TIMEZONE")) {
                if (rs.next()) {
                    System.out.println("[PostgresIntegrationTest] Effective PostgreSQL session timezone: "
                            + rs.getString(1) + ", JVM default timezone: " + java.time.ZoneId.systemDefault());
                }
            }

            this.isDatasourceVerified = true;
        }

        cleanTestData();
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        if (!isDatasourceVerified) {
            return;
        }
        jdbcTemplate.update("DELETE FROM attendance_location_checks WHERE tracking_id IN (SELECT id FROM attendance_tracking WHERE session_id IN (SELECT id FROM attendance_sessions WHERE title LIKE 'TEST-%'))");
        jdbcTemplate.update("DELETE FROM attendance_location_audit WHERE tracking_id IN (SELECT id FROM attendance_tracking WHERE session_id IN (SELECT id FROM attendance_sessions WHERE title LIKE 'TEST-%'))");
        jdbcTemplate.update("DELETE FROM attendance_tracking WHERE session_id IN (SELECT id FROM attendance_sessions WHERE title LIKE 'TEST-%')");
        jdbcTemplate.update("DELETE FROM attendance WHERE session_id IN (SELECT id FROM attendance_sessions WHERE title LIKE 'TEST-%')");
        jdbcTemplate.update("DELETE FROM attendance_sessions WHERE title LIKE 'TEST-%'");
        jdbcTemplate.update("DELETE FROM member_signups WHERE email LIKE '%@test-disposable.local'");
        jdbcTemplate.update("DELETE FROM members WHERE email LIKE '%@test-disposable.local'");
        jdbcTemplate.update("DELETE FROM batches WHERE name LIKE 'TEST-%'");
    }

    @Test
    @Transactional
    @DisplayName("1. MemberSignupRepository.findPendingByIdForUpdate locks with 'FOR UPDATE OF s' and fetches joined batch")
    void testFindPendingByIdForUpdateLocksAndJoinsBatch() {
        String batchName = "TEST-Batch-Approval";
        batchRepository.create(batchName, "ACTIVE");
        Optional<Batch> createdBatch = batchRepository.findByName(batchName);
        assertTrue(createdBatch.isPresent(), "Test batch must be created");
        Long batchId = createdBatch.get().getId();

        String email = "signup-test@test-disposable.local";
        int insertResult = memberSignupRepository.insert(
                "Jane Doe",
                email,
                "$2a$10$abcdefghijklmnopqrstuvwxyz012345678901234567890123456",
                batchId,
                "STU-PG-001"
        );
        assertEquals(1, insertResult, "Pending signup must be inserted");

        Optional<MemberSignup> insertedSignup = memberSignupRepository.findByEmail(email);
        assertTrue(insertedSignup.isPresent(), "Inserted signup must be found by email");
        Long signupId = insertedSignup.get().id();

        // Execute the query under test within the transaction:
        // Must succeed without throwing PostgreSQL error 0A000: SELECT FOR UPDATE cannot be applied to nullable side of outer join
        Optional<MemberSignup> lockedSignup = memberSignupRepository.findPendingByIdForUpdate(signupId);

        assertTrue(lockedSignup.isPresent(), "Locked signup must be present");
        assertEquals(signupId, lockedSignup.get().id());
        assertEquals("PENDING", lockedSignup.get().status());
        assertEquals(batchName, lockedSignup.get().requestedBatchName(), "Joined batch name from batches table must match");
        assertEquals("STU-PG-001", lockedSignup.get().studentId());
    }

    @Test
    @Transactional
    @DisplayName("2. AttendanceTrackingRepository.startTracking retrieves generated ID and persists record")
    void testStartTrackingRetrievesGeneratedId() {
        String sessionTitle = "TEST-Session-Tracking";
        AttendanceSession session = new AttendanceSession(
                null,
                sessionTitle,
                LocalDateTime.now().minusMinutes(10),
                LocalDateTime.now().plusMinutes(50),
                12.9716,
                77.5946,
                100
        );
        assertEquals(1, sessionRepository.createSession(session), "Session must be created");

        Long sessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM attendance_sessions WHERE title = ?",
                Long.class,
                sessionTitle
        );
        assertNotNull(sessionId, "Session ID must be retrieved");

        String memberEmail = "member-tracker@test-disposable.local";
        Member member = new Member();
        member.setName("Tracking Test Member");
        member.setEmail(memberEmail);
        member.setPasswordHash("$2a$10$abcdefghijklmnopqrstuvwxyz012345678901234567890123456");
        member.setRole("MEMBER");
        assertEquals(1, memberRepository.addMember(member), "Member must be added");

        Long memberId = memberRepository.getMemberByEmail(memberEmail).getId();
        assertNotNull(memberId, "Member ID must be retrieved");

        LocalDateTime startedAt = LocalDateTime.now();

        // Execute method under test:
        Long generatedTrackingId = trackingRepository.startTracking(memberId, sessionId, startedAt);

        assertNotNull(generatedTrackingId, "Generated tracking ID must not be null in PostgreSQL");
        assertTrue(generatedTrackingId > 0, "Generated tracking ID must be positive");

        AttendanceTracking persisted = trackingRepository.findByMemberAndSession(memberId, sessionId);
        assertNotNull(persisted, "Persisted tracking record must be found");
        assertEquals(generatedTrackingId, persisted.getId(), "Persisted ID must match generated ID");
        assertEquals("WAITING_FOR_GEOFENCE", persisted.getStatus());
        assertEquals(0, persisted.getTotalChecks());
        assertEquals(0, persisted.getInsideChecks());
    }

    @Test
    @Transactional
    @DisplayName("3. AttendanceSessionRepository.findSessions filters UPCOMING, ACTIVE, and COMPLETED using CURRENT_TIMESTAMP")
    void testFindSessionsFiltersByTimestampSemantics() {
        LocalDateTime now = LocalDateTime.now();

        // Verify clock alignment between JVM wall-clock and PostgreSQL CURRENT_TIMESTAMP
        LocalDateTime dbNow = jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP::timestamp", LocalDateTime.class);
        assertNotNull(dbNow, "Database current timestamp must be retrievable");
        long clockDriftSeconds = Math.abs(java.time.Duration.between(now, dbNow).getSeconds());
        assertTrue(clockDriftSeconds < 60,
                "JVM clock (" + now + ") and PostgreSQL CURRENT_TIMESTAMP (" + dbNow
                        + ") must be synchronized within 60s to ensure deterministic filter evaluation (drift: "
                        + clockDriftSeconds + "s)");

        AttendanceSession upcoming = new AttendanceSession(
                null,
                "TEST-Upcoming-Session",
                now.plusHours(1),
                now.plusHours(2),
                12.9716,
                77.5946,
                100
        );
        AttendanceSession active = new AttendanceSession(
                null,
                "TEST-Active-Session",
                now.minusMinutes(30),
                now.plusMinutes(30),
                12.9716,
                77.5946,
                100
        );
        AttendanceSession completed = new AttendanceSession(
                null,
                "TEST-Completed-Session",
                now.minusHours(2),
                now.minusHours(1),
                12.9716,
                77.5946,
                100
        );

        sessionRepository.createSession(upcoming);
        sessionRepository.createSession(active);
        sessionRepository.createSession(completed);

        // 1. ACTIVE filter
        List<AttendanceSession> activeSessions = sessionRepository.findSessions(
                "TEST-", "ACTIVE", null, null, "startTime", "ASC", 0, 10
        );
        assertEquals(1, activeSessions.size(), "Should find exactly 1 active test session");
        assertEquals("TEST-Active-Session", activeSessions.get(0).getTitle());
        assertEquals(1, sessionRepository.countSessions("TEST-", "ACTIVE", null, null));

        // 2. UPCOMING filter
        List<AttendanceSession> upcomingSessions = sessionRepository.findSessions(
                "TEST-", "UPCOMING", null, null, "startTime", "ASC", 0, 10
        );
        assertEquals(1, upcomingSessions.size(), "Should find exactly 1 upcoming test session");
        assertEquals("TEST-Upcoming-Session", upcomingSessions.get(0).getTitle());
        assertEquals(1, sessionRepository.countSessions("TEST-", "UPCOMING", null, null));

        // 3. COMPLETED filter
        List<AttendanceSession> completedSessions = sessionRepository.findSessions(
                "TEST-", "COMPLETED", null, null, "startTime", "ASC", 0, 10
        );
        assertEquals(1, completedSessions.size(), "Should find exactly 1 completed test session");
        assertEquals("TEST-Completed-Session", completedSessions.get(0).getTitle());
        assertEquals(1, sessionRepository.countSessions("TEST-", "COMPLETED", null, null));
    }
}
