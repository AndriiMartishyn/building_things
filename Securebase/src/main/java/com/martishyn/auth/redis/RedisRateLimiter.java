package com.martishyn.auth.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RedisRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    private final long windowMillis = 10_000;
    private final int limit = 10;

    public RedisRateLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //increment value and check if value < window
    // each window has a counter
    //every request increments counter
    public boolean isAllowedRequest(String userAddress) {
        var keyValue = userAddress + ":" + Instant.now().getEpochSecond();
        final Long incrementNumber = redisTemplate.opsForValue().increment(keyValue);
        if (incrementNumber != null && incrementNumber == 1) {
            redisTemplate.expire(keyValue, 10, TimeUnit.SECONDS);
        }
        return incrementNumber != null && incrementNumber <= limit;
    }

    /**
     *   KEY: "ratelimit:127.0.0.1"
     *      ├── (member="uuid-a", score=1720000000123)
     *      ├── (member="uuid-b", score=1720000000456)
     *      ├── (member="uuid-c", score=1720000000789)
     *      └── ...
     * @param userAddress
     * @return
     */
    public boolean isAllowedRequestSlidingWindow(String userAddress, Long configuredWindow) {
        String keyValue = "ratelimit" + ":" + userAddress;
        long now = Instant.now().toEpochMilli();
        if (configuredWindow == null) {
            configuredWindow = this.windowMillis;
        }
        long cutOff = now - configuredWindow;
        redisTemplate.opsForZSet().removeRangeByScore(keyValue, 0, cutOff); //remove older than window entries

        final Long survivors = redisTemplate.opsForZSet().zCard(keyValue);
        if (survivors == null || survivors >= this.limit) {
            return false;
        }
        redisTemplate.opsForZSet().add(keyValue, UUID.randomUUID().toString(), now);
        redisTemplate.expire(keyValue, configuredWindow, TimeUnit.MILLISECONDS);
        return true;
    }
}
