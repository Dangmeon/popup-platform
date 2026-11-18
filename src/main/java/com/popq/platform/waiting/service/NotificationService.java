package com.popq.platform.waiting.service;

import com.popq.platform.waiting.event.WaitingCalledEvent;
import com.popq.platform.waiting.event.WaitingRegisteredEvent;

public interface NotificationService { // 알림 서비스 인터페이스 정의

    // 대기 등록 완료 알림 발송
    void sendRegistrationNotification(WaitingRegisteredEvent event);

    // 고객 호출 알림 발송 (체크인 링크 / QR 안내)
    void sendCallNotification(WaitingCalledEvent event);
}