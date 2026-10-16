package com.geoattend.backend.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    @Test
    void testCorsConfigurationSanitizesPathsAndSupportsVercelWildcard() {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(
                config,
                "configuredCorsOrigins",
                "https://geo-attend-nine.vercel.app/login, https://custom-domain.com/path/to/page/"
        );

        CorsConfigurationSource source = config.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");

        CorsConfiguration corsConfig = source.getCorsConfiguration(request);
        assertNotNull(corsConfig);

        // Standard browser origin headers (never include paths)
        assertEquals(
                "https://geo-attend-nine.vercel.app",
                corsConfig.checkOrigin("https://geo-attend-nine.vercel.app"),
                "Origin should match after path stripping"
        );

        assertEquals(
                "https://custom-domain.com",
                corsConfig.checkOrigin("https://custom-domain.com"),
                "Custom domain origin should match after path stripping"
        );

        assertEquals(
                "https://random-preview.vercel.app",
                corsConfig.checkOrigin("https://random-preview.vercel.app"),
                "Any Vercel subdomain should match the wildcard pattern"
        );

        assertEquals(
                "http://localhost:5173",
                corsConfig.checkOrigin("http://localhost:5173"),
                "Local dev origin should match"
        );

        assertNull(
                corsConfig.checkOrigin("https://untrusted-malicious-site.com"),
                "Untrusted origins should not be allowed"
        );
    }
}
