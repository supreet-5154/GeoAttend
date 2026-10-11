package com.geoattend.backend.repository;

import com.geoattend.backend.security.ClubUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.geoattend.backend.security.LoginUser;

@Repository
public class ClubUserRepository {

    private final JdbcTemplate jdbcTemplate;

    public ClubUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ClubUser getUserById(Long userId) {

        String sql = """
                SELECT id, name, email, role
                FROM club_users
                WHERE id = ?
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {

                    ClubUser user = new ClubUser();

                    user.setId(rs.getLong("id"));
                    user.setName(rs.getString("name"));
                    user.setRole(rs.getString("role"));

                    return user;
                },
                userId
        );
    }

    public LoginUser getLoginUserByEmail(String email) {

        String sql = """
            SELECT id, name, email, role, password_hash
            FROM club_users
            WHERE email = ?
            """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {

                    LoginUser user = new LoginUser();

                    user.setId(rs.getLong("id"));
                    user.setName(rs.getString("name"));
                    user.setEmail(rs.getString("email"));
                    user.setRole(rs.getString("role"));
                    user.setPasswordHash(
                            rs.getString("password_hash")
                    );

                    return user;
                },
                email
        );
    }

    public ClubUser getUserByEmail(String email) {

        String sql = """
            SELECT id, name, email, role, password_hash
            FROM club_users
            WHERE email = ?
            """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {

                    ClubUser user = new ClubUser();

                    user.setId(rs.getLong("id"));
                    user.setName(rs.getString("name"));
                    user.setRole(rs.getString("role"));

                    return user;
                },
                email
        );
    }
}