package com.popq.platform.reservation.redis;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ReservationHoldRedisService {

    private static final String PREFIX = "reservation:hold:";
    private static final Logger log = LoggerFactory.getLogger(ReservationHoldRedisService.class);

    private final StringRedisTemplate stringRedisTemplate;

    public void savedHold(Long reservationId, int minutes) {
        String key = PREFIX + reservationId;

        try {
            stringRedisTemplate.opsForValue().set(
                    key,
                    reservationId.toString(),
                    Duration.ofMinutes(minutes)
            );
        }catch (Exception e){
            log.error(
                    "Redis HOLD 저장 실패. reservationId={}",
                    reservationId,
                    e
            );
        }


    }

    public void deleteHold(Long reservationId) {
        String key = PREFIX + reservationId;

        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.error(
                    "Redis HOLD 삭제 실패. reservationId={}",
                    reservationId,
                    e
            );
        }
    }

}
