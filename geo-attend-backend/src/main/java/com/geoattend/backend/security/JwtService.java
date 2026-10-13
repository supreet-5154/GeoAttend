package com.geoattend.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT configuration error: 'jwt.secret' (or environment variable 'JWT_SECRET') is not configured. "
                            + "Please provide a cryptographically secure key of at least 256 bits (32 bytes in UTF-8)."
            );
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT configuration error: 'jwt.secret' (or environment variable 'JWT_SECRET') has insufficient entropy ("
                            + (keyBytes.length * 8) + " bits provided). JJWT requires at least 256 bits (32 bytes in UTF-8) for HMAC-SHA256. "
                            + "Please generate a key with at least 32 UTF-8 bytes (e.g., using 'openssl rand -base64 32')."
            );
        }

        try {
            secretKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "JWT configuration error: Failed to initialize HMAC-SHA signing key: " + e.getMessage(), e
            );
        }
    }

    public String generateToken(ClubUser user) {

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("name", user.getName())
                .claim("role", user.getRole())
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + 1000 * 60 * 60
                        )
                )
                .signWith(secretKey)
                .compact();
    }

    public Long getUserId(String token) {

        String subject = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();

        return Long.parseLong(subject);
    }

    public String getRole(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public boolean isValid(String token) {

        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);

            return true;

        } catch (Exception e) {
            return false;
        }
    }
}