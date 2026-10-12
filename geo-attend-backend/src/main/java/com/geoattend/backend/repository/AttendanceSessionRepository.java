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

    public List<AttendanceSession> findSessions(
            String search, String status,
            java.time.LocalDateTime startDate, java.time.LocalDateTime endDate,
            String sortBy, String sortDirection,
            Integer offset, Integer limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, title, start_time, end_time,
                       latitude, longitude, radius_meters
                FROM attendance_sessions
                WHERE 1=1
                """);
        List<Object> params = new java.util.ArrayList<>();
        appendSessionFilters(sql, params, search, status, startDate, endDate);

        String orderCol = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "title" -> "title";
            case "endtime", "end_time" -> "end_time";
            case "id" -> "id";
            default -> "start_time";
        };
        String orderDir = "asc".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC";
        sql.append(" ORDER BY ").append(orderCol).append(" ").append(orderDir).append(", id DESC");

        if (offset != null && limit != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);
        }

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            AttendanceSession session = new AttendanceSession();
            session.setId(rs.getLong("id"));
            session.setTitle(rs.getString("title"));
            session.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
            session.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
            session.setLatitude(rs.getDouble("latitude"));
            session.setLongitude(rs.getDouble("longitude"));
            session.setRadiusMeters(rs.getInt("radius_meters"));
            return session;
        }, params.toArray());
    }

    public long countSessions(
            String search, String status,
            java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM attendance_sessions WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();
        appendSessionFilters(sql, params, search, status, startDate, endDate);

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    private void appendSessionFilters(StringBuilder sql, List<Object> params,
                                      String search, String status,
                                      java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        if (search != null && !search.isBlank()) {
            sql.append(" AND LOWER(title) LIKE ?");
            params.add("%" + search.trim().toLowerCase() + "%");
        }
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            String s = status.trim().toUpperCase();
            if ("UPCOMING".equals(s)) {
                sql.append(" AND start_time > CURRENT_TIMESTAMP");
            } else if ("ACTIVE".equals(s)) {
                sql.append(" AND (start_time <= CURRENT_TIMESTAMP AND end_time >= CURRENT_TIMESTAMP)");
            } else if ("COMPLETED".equals(s)) {
                sql.append(" AND end_time < CURRENT_TIMESTAMP");
            }
        }
        if (startDate != null) {
            sql.append(" AND end_time >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND start_time <= ?");
            params.add(endDate);
        }
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