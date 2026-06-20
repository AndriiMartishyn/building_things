package com.martishyn.auth.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class RedisRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

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
        if (incrementNumber !=null && incrementNumber == 1) {
            redisTemplate.expire(keyValue, 10, TimeUnit.SECONDS);
        }
        return incrementNumber !=null && incrementNumber <= limit;
    }
}
