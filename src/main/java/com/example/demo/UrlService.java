package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class UrlService {

    @Autowired
    private UrlRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final long CACHE_TTL_HOURS = 24;

    public String shortenUrl(String longUrl) {
        UrlMapping mapping = new UrlMapping();
        mapping.setLongUrl(longUrl);
        mapping.setCreatedAt(LocalDateTime.now());

        mapping = repository.save(mapping);

        String code = Base62Encoder.encode(mapping.getId());
        mapping.setShortCode(code);
        repository.save(mapping);

        return code;
    }

    public String getLongUrl(String shortCode) {
        // Step 1: check Redis first
        String cached = redisTemplate.opsForValue().get("url:" + shortCode);
        if (cached != null) {
            System.out.println("CACHE HIT for " + shortCode);
            return cached;
        }

        // Step 2: cache miss — go to the database
        System.out.println("CACHE MISS for " + shortCode);
        String longUrl = repository.findByShortCode(shortCode)
                .map(UrlMapping::getLongUrl)
                .orElseThrow(() -> new RuntimeException("Short URL not found"));

        // Step 3: store in Redis for next time
        redisTemplate.opsForValue().set("url:" + shortCode, longUrl, CACHE_TTL_HOURS, TimeUnit.HOURS);

        return longUrl;
    }
}