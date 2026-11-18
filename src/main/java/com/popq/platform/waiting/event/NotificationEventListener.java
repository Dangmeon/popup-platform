package com.popq.platform.waiting.event;

import com.popq.platform.waiting.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async // 비동기 / 대기 등록 시
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 트랜잭션이 완전히 DB에 커밋된 직후
    public void handleWaitingRegistered(WaitingRegisteredEvent event) {
        notificationService.sendRegistrationNotification(event); // 비동기로 알림 실행
    }

    @Async // 호출 시
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleWaitingCalled(WaitingCalledEvent event) {
        notificationService.sendCallNotification(event);
    }
}