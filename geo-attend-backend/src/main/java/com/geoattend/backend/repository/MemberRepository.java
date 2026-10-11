package com.geoattend.backend.repository;

import com.geoattend.backend.model.Member;
import com.geoattend.backend.security.LoginUser;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Member> getAllMembers() {

        String sql = "SELECT id, name, email, role FROM members";

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    Member member = new Member();
                    member.setId(rs.getLong("id"));
                    member.setName(rs.getString("name"));
                    member.setEmail(rs.getString("email"));
                    member.setRole(rs.getString("role"));
                    return member;
                }
        );
    }

    public int addMember(Member member) {

        String sql = "INSERT INTO members (name, email, role, password_hash) VALUES (?, ?, ?, ?)";

        return jdbcTemplate.update(
                sql,
                member.getName(),
                member.getEmail(),
                member.getRole() == null ? "MEMBER" : member.getRole(),
                member.getPasswordHash()
        );
    }

    public int updateMember(Long id, Member member) {

        String sql = "UPDATE members SET name = ?, email = ?, role = ?, password_hash = COALESCE(?, password_hash) WHERE id = ?";

        return jdbcTemplate.update(
                sql,
                member.getName(),
                member.getEmail(),
                member.getRole() == null ? "MEMBER" : member.getRole(),
                member.getPasswordHash(),
                id
        );
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

        String sql = "SELECT id, name, email, role FROM members WHERE id = ?";

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {
                    Member member = new Member();
                    member.setId(rs.getLong("id"));
                    member.setName(rs.getString("name"));
                    member.setEmail(rs.getString("email"));
                    member.setRole(rs.getString("role"));
                    return member;
                },
                memberId
        );
    }

    public Member getMemberByEmail(String email) {

        String sql = "SELECT id, name, email, role FROM members WHERE email = ?";

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {
                    Member member = new Member();
                    member.setId(rs.getLong("id"));
                    member.setName(rs.getString("name"));
                    member.setEmail(rs.getString("email"));
                    member.setRole(rs.getString("role"));
                    return member;
                },
                email
        );
    }

    public LoginUser getLoginUserByEmail(String email) {

        String sql = "SELECT id, name, email, role, password_hash FROM members WHERE email = ?";

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