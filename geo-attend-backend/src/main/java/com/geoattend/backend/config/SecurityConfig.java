package com.geoattend.backend.config;

import com.geoattend.backend.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // Login is public
                        .requestMatchers("/api/auth/**").permitAll()

                        // Member self-service routes
                        .requestMatchers("/api/attendance/self").hasRole("MEMBER")
                        .requestMatchers("/api/attendance/my").hasRole("MEMBER")
                        .requestMatchers("/api/attendance/my/tracking").hasRole("MEMBER")
                        .requestMatchers("/api/attendance/location-check").hasRole("MEMBER")
                        .requestMatchers("/api/attendance/tracking/start").hasRole("MEMBER")

                        // Only ADMIN can create members
                        .requestMatchers(HttpMethod.GET, "/api/members")
                        .hasAnyRole("ADMIN", "CLUB_USER")
                        .requestMatchers("/api/members/**").hasRole("ADMIN")

                        // Only ADMIN and CLUB_USER can manage sessions
                        .requestMatchers(HttpMethod.GET, "/api/sessions").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/sessions")
                        .hasAnyRole("ADMIN", "CLUB_USER")
                        .requestMatchers(HttpMethod.PUT, "/api/sessions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/sessions/**").hasRole("ADMIN")

                        // ADMIN and CLUB_USER can mark manual attendance
                        .requestMatchers("/api/attendance/manual")
                        .hasAnyRole("ADMIN", "CLUB_USER")

                        // Staff attendance routes
                        .requestMatchers(HttpMethod.POST, "/api/attendance")
                        .hasAnyRole("ADMIN", "CLUB_USER")
                        .requestMatchers(HttpMethod.PUT, "/api/attendance/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/attendance/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/session/*/checks").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/session/**")
                        .hasAnyRole("ADMIN", "CLUB_USER")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/member/**")
                        .hasAnyRole("ADMIN", "CLUB_USER")

                        // Everything else requires JWT
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173", "http://localhost:5174", "http://localhost:5175")
        );

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}