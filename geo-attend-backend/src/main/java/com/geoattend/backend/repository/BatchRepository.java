package com.geoattend.backend.repository;

import com.geoattend.backend.model.Batch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BatchRepository {

    private final JdbcTemplate jdbcTemplate;

    public BatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Batch> findAll() {
        String sql = """
            SELECT b.id, b.name, b.status, b.created_at, b.updated_at,
                   COUNT(m.id) AS member_count
            FROM batches b
            LEFT JOIN members m ON m.batch_id = b.id
            GROUP BY b.id, b.name, b.status, b.created_at, b.updated_at
            ORDER BY b.id ASC
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Batch(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null,
                rs.getLong("member_count")
        ));
    }

    public List<Batch> findActive() {
        String sql = """
            SELECT b.id, b.name, b.status, b.created_at, b.updated_at,
                   COUNT(m.id) AS member_count
            FROM batches b
            LEFT JOIN members m ON m.batch_id = b.id
            WHERE b.status = 'ACTIVE'
            GROUP BY b.id, b.name, b.status, b.created_at, b.updated_at
            ORDER BY b.id ASC
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Batch(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null,
                rs.getLong("member_count")
        ));
    }

    public List<Batch> findBatches(String search, String status,
                                  String sortBy, String sortDirection,
                                  Integer offset, Integer limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT b.id, b.name, b.status, b.created_at, b.updated_at,
                   COUNT(m.id) AS member_count
            FROM batches b
            LEFT JOIN members m ON m.batch_id = b.id
            WHERE 1=1
        """);
        List<Object> params = new java.util.ArrayList<>();

        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            sql.append(" AND b.status = ?");
            params.add(status.toUpperCase());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND LOWER(b.name) LIKE ?");
            params.add("%" + search.trim().toLowerCase() + "%");
        }

        sql.append(" GROUP BY b.id, b.name, b.status, b.created_at, b.updated_at");

        String orderCol = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "status" -> "b.status";
            case "membercount", "member_count" -> "member_count";
            case "createdat", "created_at" -> "b.created_at";
            case "id" -> "b.id";
            default -> "b.name";
        };
        String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        sql.append(" ORDER BY ").append(orderCol).append(" ").append(orderDir).append(", b.id ASC");

        if (offset != null && limit != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);
        }

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new Batch(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null,
                rs.getLong("member_count")
        ), params.toArray());
    }

    public long countBatches(String search, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM batches b WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            sql.append(" AND b.status = ?");
            params.add(status.toUpperCase());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND LOWER(b.name) LIKE ?");
            params.add("%" + search.trim().toLowerCase() + "%");
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public Optional<Batch> findById(Long id) {
        String sql = """
            SELECT b.id, b.name, b.status, b.created_at, b.updated_at,
                   COUNT(m.id) AS member_count
            FROM batches b
            LEFT JOIN members m ON m.batch_id = b.id
            WHERE b.id = ?
            GROUP BY b.id, b.name, b.status, b.created_at, b.updated_at
        """;
        List<Batch> results = jdbcTemplate.query(sql, (rs, rowNum) -> new Batch(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null,
                rs.getLong("member_count")
        ), id);
        return results.stream().findFirst();
    }

    public Optional<Batch> findByName(String name) {
        String sql = "SELECT id, name, status, created_at, updated_at, 0 AS member_count FROM batches WHERE name = ?";
        List<Batch> results = jdbcTemplate.query(sql, (rs, rowNum) -> new Batch(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("status"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
                rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null,
                0L
        ), name);
        return results.stream().findFirst();
    }

    public int create(String name, String status) {
        return jdbcTemplate.update(
                "INSERT INTO batches (name, status) VALUES (?, ?)",
                name, status != null ? status : "ACTIVE"
        );
    }

    public int update(Long id, String name, String status) {
        return jdbcTemplate.update(
                "UPDATE batches SET name = ?, status = ? WHERE id = ?",
                name, status, id
        );
    }

    public int updateStatus(Long id, String status) {
        return jdbcTemplate.update(
                "UPDATE batches SET status = ? WHERE id = ?",
                status, id
        );
    }
}
