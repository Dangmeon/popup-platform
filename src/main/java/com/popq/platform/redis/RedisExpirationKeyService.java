package com.popq.platform.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisExpirationKeyService {

    private final StringRedisTemplate stringRedisTemplate;

    public void save(String key, String value, int minutes) {

        stringRedisTemplate.opsForValue().set(
                key,
                value,
                Duration.ofMinutes(minutes));
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }
}
