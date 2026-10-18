package com.geoattend.backend.repository;

import com.geoattend.backend.model.Member;
import com.geoattend.backend.security.LoginUser;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String MEMBER_SELECT = """
        SELECT m.id, m.name, m.email, m.role, m.batch_id, b.name AS batch_name, m.student_id
        FROM members m
        LEFT JOIN batches b ON b.id = m.batch_id
    """;

    public MemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Member> getAllMembers() {
        String sql = MEMBER_SELECT + " ORDER BY m.id ASC";

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Member member = new Member();
                    member.setId(rs.getLong("id"));
                    member.setName(rs.getString("name"));
                    member.setEmail(rs.getString("email"));
                    member.setRole(rs.getString("role"));
                    member.setBatchId(rs.getObject("batch_id", Long.class));
                    member.setBatchName(rs.getString("batch_name"));
                    member.setStudentId(rs.getString("student_id"));
                    return member;
                }
        );
    }

    public List<Member> getMembersByBatchId(Long batchId) {
        String sql = MEMBER_SELECT + " WHERE m.batch_id = ? ORDER BY m.id ASC";

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Member member = new Member();
                    member.setId(rs.getLong("id"));
                    member.setName(rs.getString("name"));
                    member.setEmail(rs.getString("email"));
                    member.setRole(rs.getString("role"));
                    member.setBatchId(rs.getObject("batch_id", Long.class));
                    member.setBatchName(rs.getString("batch_name"));
                    member.setStudentId(rs.getString("student_id"));
                    return member;
                },
                batchId
        );
    }

    public List<Member> findMembers(String search, Long batchId, String role,
                                   String sortBy, String sortDirection,
                                   Integer offset, Integer limit) {
        StringBuilder sql = new StringBuilder(MEMBER_SELECT).append(" WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (batchId != null) {
            sql.append(" AND m.batch_id = ?");
            params.add(batchId);
        }
        if (role != null && !role.isBlank() && !"all".equalsIgnoreCase(role)) {
            sql.append(" AND m.role = ?");
            params.add(role.toUpperCase());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(m.name) LIKE ? OR LOWER(m.email) LIKE ? OR LOWER(m.student_id) LIKE ?)");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }

        String orderCol = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "email" -> "m.email";
            case "role" -> "m.role";
            case "batch", "batchname", "batch_name" -> "b.name";
            case "studentid", "student_id" -> "m.student_id";
            case "id" -> "m.id";
            default -> "m.name";
        };
        String orderDir = "desc".equalsIgnoreCase(sortDirection) ? "DESC" : "ASC";
        sql.append(" ORDER BY ").append(orderCol).append(" ").append(orderDir).append(", m.id ASC");

        if (offset != null && limit != null) {
            sql.append(" LIMIT ? OFFSET ?");
            params.add(limit);
            params.add(offset);
        }

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Member member = new Member();
            member.setId(rs.getLong("id"));
            member.setName(rs.getString("name"));
            member.setEmail(rs.getString("email"));
            member.setRole(rs.getString("role"));
            member.setBatchId(rs.getObject("batch_id", Long.class));
            member.setBatchName(rs.getString("batch_name"));
            member.setStudentId(rs.getString("student_id"));
            return member;
        }, params.toArray());
    }

    public long countMembers(String search, Long batchId, String role) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM members m LEFT JOIN batches b ON b.id = m.batch_id WHERE 1=1");
        List<Object> params = new java.util.ArrayList<>();

        if (batchId != null) {
            sql.append(" AND m.batch_id = ?");
            params.add(batchId);
        }
        if (role != null && !role.isBlank() && !"all".equalsIgnoreCase(role)) {
            sql.append(" AND m.role = ?");
            params.add(role.toUpperCase());
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (LOWER(m.name) LIKE ? OR LOWER(m.email) LIKE ? OR LOWER(m.student_id) LIKE ?)");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }

        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    public int addMember(Member member) {
        String sql = "INSERT INTO members (name, email, role, password_hash, batch_id, student_id) VALUES (?, ?, ?, ?, ?, ?)";

        return jdbcTemplate.update(
                sql,
                member.getName(),
                member.getEmail(),
                member.getRole() == null ? "MEMBER" : member.getRole(),
                member.getPasswordHash(),
                member.getBatchId(),
                member.getStudentId()
        );
    }

    public int updateMember(Long id, Member member) {
        String sql = """
            UPDATE members
            SET name = ?, email = ?, role = ?, password_hash = COALESCE(?, password_hash),
                batch_id = ?, student_id = ?
            WHERE id = ?
        """;

        return jdbcTemplate.update(
                sql,
                member.getName(),
                member.getEmail(),
                member.getRole() == null ? "MEMBER" : member.getRole(),
                member.getPasswordHash(),
                member.getBatchId(),
                member.getStudentId(),
                id
        );
    }

    public int updateMemberBatch(Long memberId, Long batchId) {
        String sql = "UPDATE members SET batch_id = ? WHERE id = ?";
        return jdbcTemplate.update(sql, batchId, memberId);
    }

    public int deleteMember(Long id) {
        String sql = "DELETE FROM members WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    public boolean memberExists(Long memberId) {
        String sql = "SELECT COUNT(*) FROM members WHERE id = ?";

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                memberId
        );

        return count != null && count > 0;
    }

    public Member getMemberById(Long memberId) {
        String sql = MEMBER_SELECT + " WHERE m.id = ?";

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {
                    Member member = new Member();
                    member.setId(rs.getLong("id"));
                    member.setName(rs.getString("name"));
                    member.setEmail(rs.getString("email"));
                    member.setRole(rs.getString("role"));
                    member.setBatchId(rs.getObject("batch_id", Long.class));
                    member.setBatchName(rs.getString("batch_name"));
                    member.setStudentId(rs.getString("student_id"));
                    return member;
                },
                memberId
        );
    }

    public Member getMemberByEmail(String email) {
        String sql = MEMBER_SELECT + " WHERE LOWER(m.email) = LOWER(?)";

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {
                    Member member = new Member();
                    member.setId(rs.getLong("id"));
                    member.setName(rs.getString("name"));
                    member.setEmail(rs.getString("email"));
                    member.setRole(rs.getString("role"));
                    member.setBatchId(rs.getObject("batch_id", Long.class));
                    member.setBatchName(rs.getString("batch_name"));
                    member.setStudentId(rs.getString("student_id"));
                    return member;
                },
                email
        );
    }

    public LoginUser getLoginUserByEmail(String email) {
        String sql = "SELECT id, name, email, role, password_hash FROM members WHERE LOWER(email) = LOWER(?)";

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {
                    LoginUser user = new LoginUser();
                    user.setId(rs.getLong("id"));
                    user.setName(rs.getString("name"));
                    user.setEmail(rs.getString("email"));
                    user.setRole(rs.getString("role"));
                    user.setPasswordHash(rs.getString("password_hash"));
                    return user;
                },
                email
        );
    }
}