package com.geoattend.backend.repository;

import com.geoattend.backend.model.AttendanceTracking;
import com.geoattend.backend.model.AttendanceLocationAudit;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceTrackingRepository {

    private static final String TRACKING_SELECT = """
            SELECT t.id, t.session_id, t.member_id, m.name AS member_name,
                   s.title AS session_title, s.start_time AS session_start_time,
                   s.end_time AS session_end_time,
                   t.started_at, t.last_check_at, t.total_checks, t.inside_checks,
                   t.status, t.finalized_at
            FROM attendance_tracking t
            JOIN attendance_sessions s ON s.id = t.session_id
            JOIN members m ON m.id = t.member_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public AttendanceTrackingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AttendanceTracking findByMemberAndSession(Long memberId, Long sessionId) {
        List<AttendanceTracking> results = jdbcTemplate.query(
                TRACKING_SELECT + " WHERE t.member_id = ? AND t.session_id = ?",
                this::mapTracking,
                memberId,
                sessionId
        );
        return results.isEmpty() ? null : results.get(0);
    }

    public List<AttendanceTracking> findByMember(Long memberId) {
        return jdbcTemplate.query(
                TRACKING_SELECT + " WHERE t.member_id = ? ORDER BY s.end_time DESC",
                this::mapTracking,
                memberId
        );
    }

    public List<AttendanceTracking> findBySession(Long sessionId) {
        return jdbcTemplate.query(
                TRACKING_SELECT + " WHERE t.session_id = ? ORDER BY m.name",
                this::mapTracking,
                sessionId
        );
    }

    public List<AttendanceLocationAudit> findLocationAuditBySession(Long sessionId) {
        return jdbcTemplate.query(
                """
                  SELECT a.tracking_id, a.check_number, a.checked_at, m.name AS member_name,
                      s.title AS session_title,
                       a.latitude, a.longitude, a.accuracy_meters, a.distance_meters,
                       a.inside_geofence
                FROM attendance_location_audit a
                JOIN attendance_tracking t ON t.id = a.tracking_id
                JOIN members m ON m.id = t.member_id
                JOIN attendance_sessions s ON s.id = t.session_id
                WHERE t.session_id = ?
                ORDER BY a.checked_at DESC
                """,
                (rs, rowNum) -> {
                    AttendanceLocationAudit audit = new AttendanceLocationAudit();
                    audit.setTrackingId(rs.getLong("tracking_id"));
                    audit.setCheckNumber(rs.getInt("check_number"));
                    audit.setCheckedAt(rs.getTimestamp("checked_at").toLocalDateTime());
                    audit.setMemberName(rs.getString("member_name"));
                    audit.setSessionTitle(rs.getString("session_title"));
                    audit.setLatitude(rs.getDouble("latitude"));
                    audit.setLongitude(rs.getDouble("longitude"));
                    audit.setAccuracyMeters(rs.getObject("accuracy_meters", Double.class));
                    audit.setDistanceMeters(rs.getDouble("distance_meters"));
                    audit.setInsideGeofence(rs.getBoolean("inside_geofence"));
                    return audit;
                },
                sessionId
        );
    }

    public List<AttendanceTracking> findDueForFinalization(LocalDateTime now) {
        return jdbcTemplate.query(
            TRACKING_SELECT + " WHERE t.status IN ('TRACKING', 'WAITING_FOR_GEOFENCE') AND s.end_time <= ?",
                this::mapTracking,
                now
        );
    }

    public Long startTracking(Long memberId, Long sessionId, LocalDateTime checkedAt) {
        jdbcTemplate.update(
                """
                INSERT INTO attendance_tracking
                    (session_id, member_id, started_at, last_check_at, total_checks, inside_checks, status)
                VALUES (?, ?, ?, ?, 0, 0, 'WAITING_FOR_GEOFENCE')
                """,
                sessionId,
                memberId,
                checkedAt,
                checkedAt
        );

        Long trackingId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return trackingId;
    }

    public void recordLocationCheck(Long trackingId, int checkNumber, LocalDateTime checkedAt,
            double latitude, double longitude, Double accuracyMeters, double distanceMeters,
            boolean insideGeofence) {
        insertLocationCheck(trackingId, checkNumber, checkedAt, latitude, longitude, insideGeofence);
        jdbcTemplate.update(
                """
                INSERT INTO attendance_location_audit
                    (tracking_id, check_number, checked_at, latitude, longitude,
                     accuracy_meters, distance_meters, inside_geofence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                trackingId,
                checkNumber,
                checkedAt,
                latitude,
                longitude,
                accuracyMeters,
                distanceMeters,
                insideGeofence
        );
        jdbcTemplate.update(
                """
                UPDATE attendance_tracking
                SET last_check_at = ?, total_checks = total_checks + 1,
                    inside_checks = inside_checks + ?,
                    status = CASE WHEN ? THEN 'TRACKING' ELSE status END
                WHERE id = ? AND status IN ('TRACKING', 'WAITING_FOR_GEOFENCE')
                """,
                checkedAt,
                insideGeofence ? 1 : 0,
                insideGeofence,
                trackingId
        );
    }

    public void updateStatus(Long trackingId, String status, LocalDateTime finalizedAt) {
        jdbcTemplate.update(
                "UPDATE attendance_tracking SET status = ?, finalized_at = ? WHERE id = ?",
                status,
                finalizedAt,
                trackingId
        );
    }

    public void markManualOverride(Long memberId, Long sessionId, LocalDateTime finalizedAt) {
        jdbcTemplate.update(
                """
                UPDATE attendance_tracking
                SET status = 'MANUAL_OVERRIDE', finalized_at = ?
                WHERE member_id = ? AND session_id = ?
                """,
                finalizedAt,
                memberId,
                sessionId
        );
    }

    public boolean hasTrackingForSession(Long sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance_tracking WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        return count != null && count > 0;
    }

    private void insertLocationCheck(Long trackingId, int checkNumber, LocalDateTime checkedAt,
            double latitude, double longitude, boolean insideGeofence) {
        jdbcTemplate.update(
                """
                INSERT INTO attendance_location_checks
                    (tracking_id, check_number, checked_at, latitude, longitude, inside_geofence)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                trackingId,
                checkNumber,
                checkedAt,
                latitude,
                longitude,
                insideGeofence
        );
    }

    private AttendanceTracking mapTracking(ResultSet rs, int rowNum) throws SQLException {
        AttendanceTracking tracking = new AttendanceTracking();
        tracking.setId(rs.getLong("id"));
        tracking.setSessionId(rs.getLong("session_id"));
        tracking.setMemberId(rs.getLong("member_id"));
        tracking.setMemberName(rs.getString("member_name"));
        tracking.setSessionTitle(rs.getString("session_title"));
        tracking.setSessionStartTime(rs.getTimestamp("session_start_time").toLocalDateTime());
        tracking.setSessionEndTime(rs.getTimestamp("session_end_time").toLocalDateTime());
        tracking.setStartedAt(rs.getTimestamp("started_at").toLocalDateTime());
        tracking.setLastCheckAt(rs.getTimestamp("last_check_at").toLocalDateTime());
        tracking.setTotalChecks(rs.getInt("total_checks"));
        tracking.setInsideChecks(rs.getInt("inside_checks"));
        tracking.setStatus(rs.getString("status"));
        var finalizedAt = rs.getTimestamp("finalized_at");
        tracking.setFinalizedAt(finalizedAt == null ? null : finalizedAt.toLocalDateTime());
        return tracking;
    }
}