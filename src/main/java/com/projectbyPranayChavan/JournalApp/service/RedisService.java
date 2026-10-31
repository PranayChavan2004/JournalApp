package com.projectbyPranayChavan.JournalApp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper mapper;

    public RedisService(@Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate, ObjectMapper mapper) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
    }

    public <T> T get(String key, Class<T> entityClass) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return mapper.readValue(json, entityClass);
        } catch (Exception e) {
            log.warn("Redis read skipped ({}): {}", key, e.getMessage());
            return null;
        }
    }

    /** ttl = time to live in seconds: how long the entry stays in the cache. */
    public void set(String key, Object value, Long ttl) {
        try {
            redisTemplate.opsForValue().set(key, mapper.writeValueAsString(value), ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis write skipped ({}): {}", key, e.getMessage());
        }
    }
}
