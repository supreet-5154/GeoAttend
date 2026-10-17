package com.geoattend.backend.repository;

import com.geoattend.backend.model.Attendance;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public AttendanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        java.math.BigDecimal val = rs.getBigDecimal(column);
        return val != null ? val.doubleValue() : null;
    }

    public int markAttendance(Attendance attendance) {

        String sql = """
                INSERT INTO attendance
                (session_id, member_id, marked_at,
                 latitude, longitude, attendance_method)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        return jdbcTemplate.update(
                sql,
                attendance.getSessionId(),
                attendance.getMemberId(),
                attendance.getMarkedAt(),
                attendance.getLatitude(),
                attendance.getLongitude(),
                attendance.getAttendanceMethod()
        );
    }

    public boolean attendanceExists(Long memberId, Long sessionId) {

        String sql = """
            SELECT COUNT(*)
            FROM attendance
            WHERE member_id = ?
              AND session_id = ?
            """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                memberId,
                sessionId
        );

        return count != null && count > 0;
    }

    public boolean hasAttendanceForSession(Long sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE session_id = ?",
                Integer.class,
                sessionId
        );
        if (count != null && count > 0) {
            return true;
        }
        Integer trackingCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM attendance_tracking WHERE session_id = ?",
            Integer.class,
            sessionId
        );
        return trackingCount != null && trackingCount > 0;
    }

    public boolean attendanceExistsById(Long attendanceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE id = ?",
                Integer.class,
                attendanceId
        );
        return count != null && count > 0;
    }

    public boolean attendanceExistsForAnotherRecord(Long memberId, Long sessionId, Long attendanceId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM attendance WHERE member_id = ? AND session_id = ? AND id <> ?",
                Integer.class,
                memberId,
                sessionId,
                attendanceId
        );
        return count != null && count > 0;
    }

    public int updateAttendance(Long attendanceId, Attendance attendance) {
        String sql = """
                UPDATE attendance
                SET session_id = ?, member_id = ?, marked_at = ?,
                    latitude = ?, longitude = ?, attendance_method = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(
                sql,
                attendance.getSessionId(),
                attendance.getMemberId(),
                attendance.getMarkedAt(),
                attendance.getLatitude(),
                attendance.getLongitude(),
                attendance.getAttendanceMethod(),
                attendanceId
        );
    }

    public int deleteAttendance(Long attendanceId) {
        return jdbcTemplate.update("DELETE FROM attendance WHERE id = ?", attendanceId);
    }

    public List<Attendance> getAttendanceBySessionId(Long sessionId) {

        String sql = """
        SELECT id, session_id, member_id, marked_at,
               latitude, longitude, attendance_method
        FROM attendance
        WHERE session_id = ?
        ORDER BY marked_at ASC
        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new Attendance(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        rs.getLong("member_id"),
                        rs.getTimestamp("marked_at").toLocalDateTime(),
                        getNullableDouble(rs, "latitude"),
                        getNullableDouble(rs, "longitude"),
                        rs.getString("attendance_method")
                ),
                sessionId
        );
    }

    public List<Attendance> getAttendanceByMemberId(Long memberId) {

        String sql = """
        SELECT id, session_id, member_id, marked_at,
               latitude, longitude, attendance_method
        FROM attendance
        WHERE member_id = ?
        ORDER BY marked_at DESC
        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new Attendance(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        rs.getLong("member_id"),
                        rs.getTimestamp("marked_at").toLocalDateTime(),
                        getNullableDouble(rs, "latitude"),
                        getNullableDouble(rs, "longitude"),
                        rs.getString("attendance_method")
                ),
                memberId
        );
    }

    public List<Attendance> getAttendanceByBatchId(Long batchId) {
        String sql = """
        SELECT a.id, a.session_id, a.member_id, a.marked_at,
               a.latitude, a.longitude, a.attendance_method
        FROM attendance a
        JOIN members m ON m.id = a.member_id
        WHERE m.batch_id = ?
        ORDER BY a.marked_at DESC
        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new Attendance(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        rs.getLong("member_id"),
                        rs.getTimestamp("marked_at").toLocalDateTime(),
                        getNullableDouble(rs, "latitude"),
                        getNullableDouble(rs, "longitude"),
                        rs.getString("attendance_method")
                ),
                batchId
        );
    }

    public List<Attendance> getAttendanceBySessionAndBatch(Long sessionId, Long batchId) {
        String sql = """
        SELECT a.id, a.session_id, a.member_id, a.marked_at,
               a.latitude, a.longitude, a.attendance_method
        FROM attendance a
        JOIN members m ON m.id = a.member_id
        WHERE a.session_id = ? AND m.batch_id = ?
        ORDER BY a.marked_at ASC
        """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new Attendance(
                        rs.getLong("id"),
                        rs.getLong("session_id"),
                        rs.getLong("member_id"),
                        rs.getTimestamp("marked_at").toLocalDateTime(),
                        getNullableDouble(rs, "latitude"),
                        getNullableDouble(rs, "longitude"),
                        rs.getString("attendance_method")
                ),
                sessionId,
                batchId
        );
    }

    public List<Attendance> findAttendance(
            Long sessionId,
            Long memberId,
            Long batchId,
            String search,
            String method,
            String status,
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            String sortBy,
            String sortDirection,
            Integer offset,
            Integer limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT a.id, a.session_id, a.member_id, a.marked_at,
                   a.latitude, a.longitude, a.attendance_method,
                   s.title AS session_title,
                   m.name AS member_name, m.student_id, m.batch_id,
                   b.name AS batch_name,
                   COALESCE(t.status, 'PRESENT') AS tracking_status
            FROM attendance a
            JOIN attendance_sessions s ON s.id = a.session_id
            JOIN members m ON m.id = a.member_id
            LEFT JOIN batches b ON b.id = m.batch_id
            LEFT JOIN attendance_tracking t ON t.session_id = a.session_id AND t.member_id = a.member_id
            WHERE 1=1
        """);

        List<Object> params = new java.util.ArrayList<>();
        appendAttendanceFilters(sql, params, sessionId, memberId, batchId, search, method, status, startDate, endDate);

        String orderCol = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "membername", "name", "member_name" -> "m.name";
            case "sessiontitle", "session_title", "title" -> "s.title";
            case "method", "attendancemethod", "attendance_method" -> "a.attendance_method";
            case "id" -> "a.id";
            case "status", "trackingstatus", "tracking_status" -> "tracking_status";
            default -> "a.marked_at";
        };
        String orderDir = "asc".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(orderCol).append(" ").append(orderDir).append(", a.id DESC");

        if (offset != null && limit != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);
        }

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new Attendance(
                rs.getLong("id"),
                rs.getLong("session_id"),
                rs.getLong("member_id"),
                rs.getTimestamp("marked_at").toLocalDateTime(),
                getNullableDouble(rs, "latitude"),
                getNullableDouble(rs, "longitude"),
                rs.getString("attendance_method"),
                rs.getString("session_title"),
                rs.getString("member_name"),
                rs.getString("student_id"),
                rs.getObject("batch_id", Long.class),
                rs.getString("batch_name"),
                rs.getString("tracking_status")
        ), params.toArray());
    }

    public long countAttendance(
            Long sessionId,
            Long memberId,
            Long batchId,
            String search,
            String method,
            String status,
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM attendance a
            JOIN attendance_sessions s ON s.id = a.session_id
            JOIN members m ON m.id = a.member_id
            LEFT JOIN batches b ON b.id = m.batch_id
            LEFT JOIN attendance_tracking t ON t.session_id = a.session_id AND t.member_id = a.member_id
            WHERE 1=1
        """);

        List<Object> params = new java.util.ArrayList<>();
        appendAttendanceFilters(sql, params, sessionId, memberId, batchId, search, method, status, startDate, endDate);

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    private void appendAttendanceFilters(StringBuilder sql, List<Object> params,
                                         Long sessionId, Long memberId, Long batchId,
                                         String search, String method, String status,
                                         java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        if (sessionId != null) {
            sql.append(" AND a.session_id = ?");
            params.add(sessionId);
        }
        if (memberId != null) {
            sql.append(" AND a.member_id = ?");
            params.add(memberId);
        }
        if (batchId != null) {
            sql.append(" AND m.batch_id = ?");
            params.add(batchId);
        }
        if (method != null && !method.isBlank() && !"all".equalsIgnoreCase(method)) {
            sql.append(" AND a.attendance_method = ?");
            params.add(method.toUpperCase());
        }
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            sql.append(" AND COALESCE(t.status, 'PRESENT') = ?");
            params.add(status.toUpperCase());
        }
        if (startDate != null) {
            sql.append(" AND a.marked_at >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND a.marked_at <= ?");
            params.add(endDate);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(m.name) LIKE ? OR LOWER(m.email) LIKE ? OR LOWER(m.student_id) LIKE ? OR LOWER(s.title) LIKE ?)");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }
    }
}