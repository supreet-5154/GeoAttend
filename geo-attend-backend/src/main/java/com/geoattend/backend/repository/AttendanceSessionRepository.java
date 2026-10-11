package com.geoattend.backend.repository;

import com.geoattend.backend.model.AttendanceSession;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AttendanceSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AttendanceSession> getAllSessions() {

        String sql = """
                SELECT id, title, start_time, end_time,
                       latitude, longitude, radius_meters
                FROM attendance_sessions
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {

                    AttendanceSession session = new AttendanceSession();

                    session.setId(rs.getLong("id"));
                    session.setTitle(rs.getString("title"));
                    session.setStartTime(
                            rs.getTimestamp("start_time").toLocalDateTime()
                    );
                    session.setEndTime(
                            rs.getTimestamp("end_time").toLocalDateTime()
                    );
                    session.setLatitude(rs.getDouble("latitude"));
                    session.setLongitude(rs.getDouble("longitude"));
                    session.setRadiusMeters(rs.getInt("radius_meters"));

                    return session;
                }
        );
    }

    public int createSession(AttendanceSession session) {

        String sql = """
            INSERT INTO attendance_sessions
            (title, start_time, end_time, latitude, longitude, radius_meters)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        return jdbcTemplate.update(
                sql,
                session.getTitle(),
                session.getStartTime(),
                session.getEndTime(),
                session.getLatitude(),
                session.getLongitude(),
                session.getRadiusMeters()
        );
    }

    public int updateSession(Long sessionId, AttendanceSession session) {
        String sql = """
                UPDATE attendance_sessions
                SET title = ?, start_time = ?, end_time = ?,
                    latitude = ?, longitude = ?, radius_meters = ?
                WHERE id = ?
                """;

        return jdbcTemplate.update(
                sql,
                session.getTitle(),
                session.getStartTime(),
                session.getEndTime(),
                session.getLatitude(),
                session.getLongitude(),
                session.getRadiusMeters(),
                sessionId
        );
    }

    public int deleteSession(Long sessionId) {
        return jdbcTemplate.update("DELETE FROM attendance_sessions WHERE id = ?", sessionId);
    }

    public boolean sessionExists(Long sessionId) {

        String sql = "SELECT COUNT(*) FROM attendance_sessions WHERE id = ?";

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                sessionId
        );

        return count != null && count > 0;
    }

    public AttendanceSession getSessionById(Long sessionId) {

        String sql = """
            SELECT id, title, start_time, end_time,
                   latitude, longitude, radius_meters
            FROM attendance_sessions
            WHERE id = ?
            """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {

                    AttendanceSession session = new AttendanceSession();

                    session.setId(rs.getLong("id"));
                    session.setTitle(rs.getString("title"));
                    session.setStartTime(
                            rs.getTimestamp("start_time").toLocalDateTime()
                    );
                    session.setEndTime(
                            rs.getTimestamp("end_time").toLocalDateTime()
                    );
                    session.setLatitude(rs.getDouble("latitude"));
                    session.setLongitude(rs.getDouble("longitude"));
                    session.setRadiusMeters(rs.getInt("radius_meters"));

                    return session;
                },
                sessionId
        );
    }
}