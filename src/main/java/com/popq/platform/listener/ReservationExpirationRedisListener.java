package com.popq.platform.listener;

import com.popq.platform.service.ReservationExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationExpirationRedisListener implements MessageListener {

    private static  final String PREFIX = "reservation:hold:";

    private final ReservationExpirationService reservationExpirationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = message.toString();

        System.out.println("Redis 만료 이벤트 수신: " + key);

        if(!key.startsWith(PREFIX)) {
            return;
        }

        String reservationIdValue = key.substring(PREFIX.length());
        Long reservationId = Long.valueOf(reservationIdValue);

        System.out.println("만료 처리 reservationId: " + reservationId);

        reservationExpirationService.expireReservation(reservationId);
    }
}
