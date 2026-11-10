package com.popq.platform.reservation.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ReservationHoldRedisService {

    private static final String PREFIX = "reservation:hold:";

    private final StringRedisTemplate stringRedisTemplate;

    public void savedHold(Long reservationId, int minutes) {
        String key = PREFIX + reservationId;

        stringRedisTemplate.opsForValue().set(
                key,
                reservationId.toString(),
                Duration.ofMinutes(minutes)
        );
    }

    public void deleteHold(Long reservationId) {
        String key = PREFIX + reservationId;
        stringRedisTemplate.delete(key);
    }

}
