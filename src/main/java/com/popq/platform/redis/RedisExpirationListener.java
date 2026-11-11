package com.popq.platform.redis;

import com.popq.platform.reservation.redis.ReservationExpirationService;
import com.popq.platform.service.OrderExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisExpirationListener implements MessageListener {

    private static final String RESERVATION_PREFIX = "reservation:hold:";
    private static final String ORDER_PREFIX = "order:pending:";

    private final ReservationExpirationService reservationExpirationService;
    private final OrderExpirationService orderExpirationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {

        String key = message.toString();

        if(key.startsWith(RESERVATION_PREFIX)) {
            Long reservationId = Long.valueOf(key.substring(RESERVATION_PREFIX.length()));

            reservationExpirationService.expireReservation(reservationId);

            return;
        };

        if(key.startsWith(ORDER_PREFIX)) {
            Long orderId = Long.valueOf(key.substring(ORDER_PREFIX.length()));

            orderExpirationService.expireOrder(orderId);
        };

    }

}
