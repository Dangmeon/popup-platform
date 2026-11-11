package com.popq.platform.service;

import com.popq.platform.redis.RedisExpirationKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderPendingCreatedEventListener {

    private final RedisExpirationKeyService expirationKeyService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderPendingCreatedEvent event) {

        expirationKeyService.save(
                "order:pending:" + event.orderId(),
                event.orderId().toString(),
                event.expireMinutes()
        );
    }
}