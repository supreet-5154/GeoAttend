package com.geoattend.backend.repository;

import com.geoattend.backend.model.Attendance;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public AttendanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
                        rs.getObject("latitude", Double.class),
                        rs.getObject("longitude", Double.class),
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
                        rs.getObject("latitude", Double.class),
                        rs.getObject("longitude", Double.class),
                        rs.getString("attendance_method")
                ),
                memberId
        );
    }
}