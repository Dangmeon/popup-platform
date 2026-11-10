package com.popq.platform.service;

import com.popq.platform.dto.TimeSlotMessage;
import com.popq.platform.entity.TimeSlot;
import com.popq.platform.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationWebSocketService {

    // STOMP/WebSocket 메시지를 실제로 전송하기 위한 Spring 객체
    private final SimpMessagingTemplate messagingTemplate;
    private final TimeSlotRepository timeSlotRepository;

    public void sendMessagingTemplate(Long timeSlotId){

        // 이벤트에서는 ID만 전달받았기 때문에 DB에서 최신 TimeSlot 상태를 다시 조회
        TimeSlot timeSlot = timeSlotRepository.findById(timeSlotId)
                .orElseThrow(() -> new IllegalArgumentException("회차를 찾을 수 없습니다."));

        int remainingCount = timeSlot.getCapacity() - timeSlot.getReservedCount();
        boolean closed = remainingCount <= 0;

        System.out.println(
                "WebSocket 전송: timeSlotId=" + timeSlotId +
                ", remainingCount=" + remainingCount +
                ", closed=" + closed
        );

        // 프론트에 전달할 WebSocket 메시지 생성
        TimeSlotMessage message =
                new TimeSlotMessage(
                        timeSlot.getTimeSlotId(),
                        timeSlot.getCapacity(),
                        timeSlot.getReservedCount(),
                        remainingCount,
                        closed
                );

        // 해당 TimeSlot을 구독하고 있는 프론트에 메시지 전송
        // 이 주소를 구독 중인 프론트는 새로고침 없이 변경된 자리 정보를 받을 수 있음
        messagingTemplate.convertAndSend(
                "/topic/timeSlot/" + timeSlotId, message);
    }
}
