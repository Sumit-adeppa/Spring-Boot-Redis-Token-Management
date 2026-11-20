package com.example.demo.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import com.example.demo.security.JwtTokenProvider;

@Repository
public class TokenRepository {
    private static final String ACCESS_PREFIX = "user:access:";
    private static final String REFRESH_PREFIX = "user:refresh:";
    private static final String ACCESS_BLACKLIST_PREFIX = "blacklist:access:";
    private static final String REFRESH_BLACKLIST_PREFIX = "blacklist:refresh:";

    private static final DefaultRedisScript<Long> STORE_PAIR_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3]); " +
                    "redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[4]); return 1",
                    Long.class);

    private static final DefaultRedisScript<Long> ROTATE_PAIR_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] and " +
                    "redis.call('PTTL', KEYS[1]) > 0 then " +
                    "redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3]); " +
                    "redis.call('SET', KEYS[1], ARGV[4], 'PX', ARGV[5]); " +
                    "return 1 else return 0 end",
                    Long.class);

    private static final DefaultRedisScript<Long> LOGOUT_SCRIPT =
            new DefaultRedisScript<>(
                    "local access = redis.call('GET', KEYS[1]) or ''; " +
                    "local refresh = redis.call('GET', KEYS[2]) or ''; " +
                    "if access ~= ARGV[1] or refresh ~= ARGV[2] then return 0 end; " +
                    "local accessTtl = redis.call('PTTL', KEYS[1]); " +
                    "local refreshTtl = redis.call('PTTL', KEYS[2]); " +
                    "if access ~= '' and accessTtl > 0 then " +
                    "redis.call('SET', KEYS[3], 'revoked', 'PX', accessTtl); end; " +
                    "if refresh ~= '' and refreshTtl > 0 then " +
                    "redis.call('SET', KEYS[4], 'revoked', 'PX', refreshTtl); end; " +
                    "redis.call('DEL', KEYS[1], KEYS[2]); return 1",
                    Long.class);

    private final StringRedisTemplate redis;
    private final JwtTokenProvider jwt;

    public TokenRepository(StringRedisTemplate redis, JwtTokenProvider jwt) {
        this.redis = redis;
        this.jwt = jwt;
    }

    /**
     * Store the active token pair in one Redis operation. A previous pair
     * becomes invalid because authentication checks the current token values.
     */
    public void storeTokens(String username, String accessToken, String refreshToken) {
        long accessTtl = ttlMillis(accessToken);
        long refreshTtl = ttlMillis(refreshToken);
        if (accessTtl <= 0 || refreshTtl <= 0) {
            throw new IllegalArgumentException("Cannot store expired JWTs");
        }
        redis.execute(STORE_PAIR_SCRIPT,
                List.of(ACCESS_PREFIX + username, REFRESH_PREFIX + username),
                accessToken, refreshToken, String.valueOf(accessTtl), String.valueOf(refreshTtl));
    }

    /** Atomically replace the pair only while the submitted refresh token is current. */
    public boolean rotateTokens(String username, String oldRefreshToken,
                                String newAccessToken, String newRefreshToken) {
        long accessTtl = ttlMillis(newAccessToken);
        long refreshTtl = ttlMillis(newRefreshToken);
        if (accessTtl <= 0 || refreshTtl <= 0) {
            return false;
        }

        Long result = redis.execute(ROTATE_PAIR_SCRIPT,
                List.of(REFRESH_PREFIX + username, ACCESS_PREFIX + username),
                oldRefreshToken, newAccessToken, String.valueOf(accessTtl),
                newRefreshToken, String.valueOf(refreshTtl));
        return Long.valueOf(1L).equals(result);
    }

    public String getAccessToken(String username) {
        return redis.opsForValue().get(ACCESS_PREFIX + username);
    }

    public String getRefreshToken(String username) {
        return redis.opsForValue().get(REFRESH_PREFIX + username);
    }

    public boolean isAccessTokenBlackListed(String token) {
        return Boolean.TRUE.equals(redis.hasKey(ACCESS_BLACKLIST_PREFIX + sha256(token)));
    }

    public boolean isRefreshTokenBlackListed(String token) {
        return Boolean.TRUE.equals(redis.hasKey(REFRESH_BLACKLIST_PREFIX + sha256(token)));
    }

    /**
     * Atomically blacklist the current pair for its remaining Redis TTL and
     * delete the active keys. Retry if another login/refresh changed the pair
     * between the initial read and the Lua script.
     */
    public void removeAllTokens(String username) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String access = getAccessToken(username);
            String refresh = getRefreshToken(username);

            String accessBlacklistKey = ACCESS_BLACKLIST_PREFIX + sha256(access == null ? "" : access);
            String refreshBlacklistKey = REFRESH_BLACKLIST_PREFIX + sha256(refresh == null ? "" : refresh);

            Long result = redis.execute(LOGOUT_SCRIPT,
                    List.of(ACCESS_PREFIX + username, REFRESH_PREFIX + username,
                            accessBlacklistKey, refreshBlacklistKey),
                    access == null ? "" : access,
                    refresh == null ? "" : refresh);

            if (Long.valueOf(1L).equals(result)) {
                return;
            }
        }
        throw new IllegalStateException("Could not revoke tokens because the active token pair kept changing");
    }

    private long ttlMillis(String token) {
        return Math.max(0L, jwt.getRemainingValidityMillis(token));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
