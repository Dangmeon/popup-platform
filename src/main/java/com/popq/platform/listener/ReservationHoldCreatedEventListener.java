package com.popq.platform.listener;

import com.popq.platform.entity.ReservationHoldCreatedEvent;
import com.popq.platform.service.ReservationHoldRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReservationHoldCreatedEventListener {

    private final ReservationHoldRedisService reservationHoldRedisService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ReservationHoldCreatedEvent event) {

        reservationHoldRedisService.savedHold(
                event.reservationId(),
                event.expireMinutes()
        );
    }
}