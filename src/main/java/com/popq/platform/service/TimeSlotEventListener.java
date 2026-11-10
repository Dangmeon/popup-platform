package com.popq.platform.service;

import com.popq.platform.dto.TimeSlotChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeSlotEventListener {

    private final ReservationWebSocketService reservationWebSocketService;

    // ReservationService에서 발생시킨 TimeSlotChangedEvent를 받는 메서드
    // AFTER_COMMIT : // DB 변경이 정상적으로 Commit된 후에만 WebSocket 전송
    // DB 저장이 실패해서 Rollback되면 WebSocket 메시지도 보내지 않음
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTimeSlot(TimeSlotChangedEvent event) {

        log.info("TimeSlot 변경 이벤트 수신. timeSlotId={}", event.timeSlotId());

        try {
            // 이벤트에 들어있는 timeSlotId를 WebSocketService로 전달
            reservationWebSocketService.sendMessagingTemplate(
                            event.timeSlotId()
            );

        } catch (Exception e) {
            log.error(
                    "TimeSlot WebSocket 전송 실패. timeSlotId={}",
                    event.timeSlotId(),
                    e
            );
        }
    }
}
