package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimiter {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final int MAX_REQUESTS = 100;
    private static final long WINDOW_SECONDS = 60;

    public boolean isAllowed(String clientId) {
        String key = "rate:" + clientId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        return count != null && count <= MAX_REQUESTS;
    }
}