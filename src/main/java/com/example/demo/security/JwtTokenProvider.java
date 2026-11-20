package com.example.demo.security;

import io.jsonwebtoken.io.Decoders;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.example.demo.dto.TokenPair;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {
    private final SecretKey signingKey;
    private final String issuer;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.issuer}") String issuer,
                            @Value("${jwt.expiration}") long accessExpirationMs,
                            @Value("${jwt.refresh-expiration}") long refreshExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.issuer = issuer;
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public TokenPair generateTokenPair(Authentication authentication) {
        UserDetails user = (UserDetails) authentication.getPrincipal();
        return generateTokenPair(user.getUsername());
    }

    public TokenPair generateTokenPair(String username) {
        return new TokenPair(generate(username, "access", accessExpirationMs),
                generate(username, "refresh", refreshExpirationMs));
    }

    public String generateAccessToken(String username) { return generate(username, "access", accessExpirationMs); }
    public String generateRefreshToken(String username) { return generate(username, "refresh", refreshExpirationMs); }

    private String generate(String username, String type, long lifetime) {
        Date now = new Date();
        return Jwts.builder().issuer(issuer).subject(username).id(UUID.randomUUID().toString())
                .issuedAt(now).expiration(new Date(now.getTime() + lifetime))
                .claim("token_type", type)
                .signWith(signingKey).compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
    }

    public boolean validateToken(String token) {
        try { parseClaims(token); return true; }
        catch (JwtException | IllegalArgumentException ex) { return false; }
    }

    public boolean isAccessToken(String token) {
        try { return "access".equals(parseClaims(token).get("token_type", String.class)); }
        catch (JwtException | IllegalArgumentException ex) { return false; }
    }

    public boolean isRefreshToken(String token) {
        try { return "refresh".equals(parseClaims(token).get("token_type", String.class)); }
        catch (JwtException | IllegalArgumentException ex) { return false; }
    }

    public String getUsernameFromToken(String token) { return parseClaims(token).getSubject(); }
    public long getRemainingValidityMillis(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return Math.max(0L, expiration.getTime() - System.currentTimeMillis());
    }
}
