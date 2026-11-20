package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.demo.security.JwtTokenProvider;

class SpringBootRedisTokenManagementApplicationTests {
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        tokenProvider = new JwtTokenProvider(secret, "test-issuer", 60_000, 300_000);
    }

    @Test
    void accessAndRefreshTokensHaveDistinctTypesAndValidClaims() {
        String access = tokenProvider.generateAccessToken("test-user");
        String refresh = tokenProvider.generateRefreshToken("test-user");
        String secondAccess = tokenProvider.generateAccessToken("test-user");

        assertFalse(access.equals(secondAccess), "Each issued JWT must have a unique identifier");
        assertTrue(tokenProvider.validateToken(access));
        assertTrue(tokenProvider.validateToken(refresh));
        assertTrue(tokenProvider.isAccessToken(access));
        assertFalse(tokenProvider.isRefreshToken(access));
        assertTrue(tokenProvider.isRefreshToken(refresh));
        assertFalse(tokenProvider.isAccessToken(refresh));
        assertEquals("test-user", tokenProvider.getUsernameFromToken(access));
        assertTrue(tokenProvider.getRemainingValidityMillis(refresh) > 0);
    }

    @Test
    void rejectsMalformedAndWrongIssuerTokens() {
        assertFalse(tokenProvider.validateToken("not-a-jwt"));

        String otherSecret = Base64.getEncoder().encodeToString(new byte[32]);
        JwtTokenProvider otherIssuer = new JwtTokenProvider(
                otherSecret, "different-issuer", 60_000, 300_000);
        String token = otherIssuer.generateAccessToken("test-user");

        assertFalse(tokenProvider.validateToken(token));
    }
}
