package com.popq.platform.reservation.redis;

import com.popq.platform.redis.RedisExpirationKeyService;
import com.popq.platform.reservation.event.ReservationHoldCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReservationHoldCreatedEventListener {

    private final RedisExpirationKeyService expirationKeyService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReservationHoldCreatedEvent event) {

        expirationKeyService.save(
                "reservation:hold:" + event.reservationId(),
                event.reservationId().toString(),
                event.expireMinutes()
        );
    }
}