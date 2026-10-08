package org.tettyrs.service;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.string.StringCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class RateLimitService {

    @Inject
    RedisDataSource redisDataSource;

    private static final String KEY_PREFIX = "rate_limit:";
    private static final int WINDOW_SECONDS = 3600;

    public record RateLimitResult(
            boolean allowed,
            long remaining,
            long resetAt,
            Optional<String> retryAfter
    ){}

    public RateLimitResult checkLimit(String userId, String endpoint, int limitPerHour){
        String key = buildKey(userId, endpoint);
        StringCommands<String, String> commands = redisDataSource.string(String.class);

        try {
            String countStr = commands.get(key);
            long count = countStr != null ? Long.parseLong(countStr) : 0;

            if (count >= limitPerHour) {
                long ttl = redisDataSource.key().pttl(key);
                long resetAt = System.currentTimeMillis() + (ttl > 0 ? ttl : WINDOW_SECONDS * 1000);

                return new RateLimitResult(
                        false,
                        0,
                        resetAt,
                        Optional.of(String.valueOf(ttl/1000))
                );
            }

            commands.incr(key);

            if (count == 0) {
                redisDataSource.key().expire(key, WINDOW_SECONDS);
            }

            long remaining = limitPerHour - count - 1;
            long resetAt = System.currentTimeMillis() + WINDOW_SECONDS * 1000;

            return new RateLimitResult(true, remaining, resetAt, Optional.empty());
        }catch (Exception e){
            Log.error("Rate limit check failed for user: "+userId+", endpoint: "+endpoint, e);
            return new RateLimitResult(true, limitPerHour -1, System.currentTimeMillis() + WINDOW_SECONDS * 1000, Optional.empty());
        }
    }

    private String buildKey(String userId, String endpoint){
        long hourTimestamp = System.currentTimeMillis() / (WINDOW_SECONDS*1000);
        return KEY_PREFIX + userId + ":" + endpoint+ ":" + hourTimestamp;
    }

    public void reset(String userId, String endpoint){
        try {
            String key = buildKey(userId, endpoint);
            redisDataSource.key().del(key);
        }catch (Exception e){
            Log.warn("Failed to reset rate limit");
        }
    }

}
