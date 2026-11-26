package com.popq.platform.order.redis;

import com.popq.platform.common.redis.RedisExpirationKeyService;
import com.popq.platform.order.event.OrderPendingCreatedEvent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPendingCreatedEventListenerTest {

    @Mock
    private RedisExpirationKeyService expirationKeyService;

    @InjectMocks
    private OrderPendingCreatedEventListener eventListener;

    @Test
    @DisplayName("주문 생성 이벤트 처리 시 Redis 만료 키와 TTL을 전달한다")
    void handle_success() {

        Long orderId = 1L;
        int expireMinutes = 10;

        OrderPendingCreatedEvent event = new OrderPendingCreatedEvent(orderId, expireMinutes);

        eventListener.handle(event);

        // Redis Key, Value 및 TTL 전달 검증
        verify(expirationKeyService, times(1))
                .save(
                        "order:pending:1",
                        "1",
                        10
                );

        verifyNoMoreInteractions(expirationKeyService);
    }
}