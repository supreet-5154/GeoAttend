package com.geoattend.backend.repository;

import com.geoattend.backend.model.MemberSignup;
import com.geoattend.backend.model.PendingMemberSignup;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class MemberSignupRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemberSignupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<MemberSignup> signupMapper = (rs, rowNum) -> new MemberSignup(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getObject("requested_batch_id", Long.class),
            rs.getString("requested_batch_name"),
            rs.getString("student_id"),
            rs.getString("status"),
            rs.getString("rejection_reason"),
            rs.getObject("reviewed_by", Long.class),
            rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toLocalDateTime() : null,
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    private final RowMapper<PendingMemberSignup> pendingMapper = (rs, rowNum) -> new PendingMemberSignup(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getObject("requested_batch_id", Long.class),
            rs.getString("requested_batch_name"),
            rs.getString("student_id"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getString("status"),
            rs.getString("rejection_reason")
    );

    public Optional<MemberSignup> findByEmail(String email) {
        String sql = """
            SELECT s.id, s.name, s.email, s.password_hash, s.requested_batch_id,
                   b.name AS requested_batch_name, s.student_id, s.status, s.rejection_reason,
                   s.reviewed_by, s.reviewed_at, s.created_at
            FROM member_signups s
            LEFT JOIN batches b ON s.requested_batch_id = b.id
            WHERE LOWER(s.email) = LOWER(?)
            ORDER BY s.id DESC
            LIMIT 1
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, signupMapper, email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<MemberSignup> findPendingByEmail(String email) {
        String sql = """
            SELECT s.id, s.name, s.email, s.password_hash, s.requested_batch_id,
                   b.name AS requested_batch_name, s.student_id, s.status, s.rejection_reason,
                   s.reviewed_by, s.reviewed_at, s.created_at
            FROM member_signups s
            LEFT JOIN batches b ON s.requested_batch_id = b.id
            WHERE LOWER(s.email) = LOWER(?) AND s.status = 'PENDING'
            LIMIT 1
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, signupMapper, email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<MemberSignup> findPendingByIdForUpdate(Long id) {
        String sql = """
            SELECT s.id, s.name, s.email, s.password_hash, s.requested_batch_id,
                   b.name AS requested_batch_name, s.student_id, s.status, s.rejection_reason,
                   s.reviewed_by, s.reviewed_at, s.created_at
            FROM member_signups s
            LEFT JOIN batches b ON s.requested_batch_id = b.id
            WHERE s.id = ? AND s.status = 'PENDING'
            FOR UPDATE OF s
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, signupMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<MemberSignup> findById(Long id) {
        String sql = """
            SELECT s.id, s.name, s.email, s.password_hash, s.requested_batch_id,
                   b.name AS requested_batch_name, s.student_id, s.status, s.rejection_reason,
                   s.reviewed_by, s.reviewed_at, s.created_at
            FROM member_signups s
            LEFT JOIN batches b ON s.requested_batch_id = b.id
            WHERE s.id = ?
        """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, signupMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int insert(String name, String email, String passwordHash, Long requestedBatchId, String studentId) {
        String sql = """
            INSERT INTO member_signups (name, email, password_hash, requested_batch_id, student_id, status)
            VALUES (?, ?, ?, ?, ?, 'PENDING')
        """;
        return jdbcTemplate.update(sql, name, email, passwordHash, requestedBatchId, studentId);
    }

    public int updateStatus(Long id, String status) {
        String sql = "UPDATE member_signups SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    public int approve(Long id, Long reviewedBy) {
        String sql = """
            UPDATE member_signups
            SET status = 'APPROVED', reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'PENDING'
        """;
        return jdbcTemplate.update(sql, reviewedBy, id);
    }

    public int reject(Long id, String rejectionReason, Long reviewedBy) {
        String sql = """
            UPDATE member_signups
            SET status = 'REJECTED', rejection_reason = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'PENDING'
        """;
        return jdbcTemplate.update(sql, rejectionReason, reviewedBy, id);
    }

    public List<PendingMemberSignup> findPending() {
        return findAll("PENDING");
    }

    public List<PendingMemberSignup> findAll(String status) {
        String sql = """
            SELECT s.id, s.name, s.email, s.requested_batch_id, b.name AS requested_batch_name,
                   s.student_id, s.created_at, s.status, s.rejection_reason
            FROM member_signups s
            LEFT JOIN batches b ON s.requested_batch_id = b.id
            WHERE (? IS NULL OR s.status = ?)
            ORDER BY s.created_at DESC
        """;
        return jdbcTemplate.query(sql, pendingMapper, status, status);
    }

    public long countSignups(String status, Long batchId, String search,
                             LocalDateTime start, LocalDateTime end) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM member_signups s
            LEFT JOIN batches b ON s.requested_batch_id = b.id
            WHERE 1=1
        """);
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND s.status = ?");
            params.add(status.toUpperCase());
        }
        if (batchId != null) {
            if (batchId == -1L) {
                sql.append(" AND s.requested_batch_id IS NULL");
            } else {
                sql.append(" AND s.requested_batch_id = ?");
                params.add(batchId);
            }
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(s.name) LIKE ? OR LOWER(s.email) LIKE ? OR LOWER(s.student_id) LIKE ?)");
            String pattern = "%" + search.trim().toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (start != null) {
            sql.append(" AND s.created_at >= ?");
            params.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND s.created_at <= ?");
            params.add(Timestamp.valueOf(end));
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public List<PendingMemberSignup> findSignups(
            String status, Long batchId, String search,
            LocalDateTime start, LocalDateTime end,
            String sortBy, String sortDirection,
            Integer offset, Integer limit) {

        StringBuilder sql = new StringBuilder("""
            SELECT s.id, s.name, s.email, s.requested_batch_id, b.name AS requested_batch_name,
                   s.student_id, s.created_at, s.status, s.rejection_reason
            FROM member_signups s
            LEFT JOIN batches b ON s.requested_batch_id = b.id
            WHERE 1=1
        """);
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND s.status = ?");
            params.add(status.toUpperCase());
        }
        if (batchId != null) {
            if (batchId == -1L) {
                sql.append(" AND s.requested_batch_id IS NULL");
            } else {
                sql.append(" AND s.requested_batch_id = ?");
                params.add(batchId);
            }
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(s.name) LIKE ? OR LOWER(s.email) LIKE ? OR LOWER(s.student_id) LIKE ?)");
            String pattern = "%" + search.trim().toLowerCase() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        if (start != null) {
            sql.append(" AND s.created_at >= ?");
            params.add(Timestamp.valueOf(start));
        }
        if (end != null) {
            sql.append(" AND s.created_at <= ?");
            params.add(Timestamp.valueOf(end));
        }

        String sortCol = "s.created_at";
        if ("name".equalsIgnoreCase(sortBy)) {
            sortCol = "s.name";
        } else if ("email".equalsIgnoreCase(sortBy)) {
            sortCol = "s.email";
        } else if ("status".equalsIgnoreCase(sortBy)) {
            sortCol = "s.status";
        } else if ("id".equalsIgnoreCase(sortBy)) {
            sortCol = "s.id";
        }

        String dir = "DESC";
        if ("asc".equalsIgnoreCase(sortDirection)) {
            dir = "ASC";
        }

        sql.append(" ORDER BY ").append(sortCol).append(" ").append(dir);

        if (limit != null && offset != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);
        }

        return jdbcTemplate.query(sql.toString(), pendingMapper, params.toArray());
    }
}
