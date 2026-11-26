package com.popq.platform.reservation.redis;

import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.reservation.event.ReservationHoldCreatedEvent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationHoldCreatedEventListenerTest {

    @Mock
    private RedisExpirationKeyService redisExpirationKeyService;

    @InjectMocks
    private ReservationHoldCreatedEventListener reservationHoldCreatedEventListener;

    @Test
    @DisplayName("예약 생성 이벤트를 수신하면 Redis 만료 키를 등록한다")
    void handle_success() {

        Long reservationId = 1L;
        int expireMinutes = 10;

        // 실제 예약 생성 이벤트 객체 생성
        ReservationHoldCreatedEvent event = new ReservationHoldCreatedEvent(reservationId, expireMinutes);

        // 예상 Redis 키 및 값
        String expectedKey = "reservation:hold:1";
        String expectedValue = "1";

        assertDoesNotThrow(
                () -> reservationHoldCreatedEventListener.handle(event)
        );

        // Redis 만료 키, 값, TTL이 정확하게 전달되는지 검증
        verify(redisExpirationKeyService).save(
                expectedKey,
                expectedValue,
                expireMinutes
        );

        verifyNoMoreInteractions(redisExpirationKeyService);
    }
}