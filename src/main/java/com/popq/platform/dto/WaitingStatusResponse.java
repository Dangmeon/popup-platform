package com.popq.platform.dto;

import com.popq.platform.entity.WaitingQueue;
import com.popq.platform.entity.WaitingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class WaitingStatusResponse {

    private Long waitingId;          // 발급된 대기열 ID
    private Long popId;              // 팝업스토어 ID
    private Integer peopleCount;           // 인원수
    private Integer queueNumber;     // 당일 대기 번호 (ex: 1번, 2번)
    private Long teamsAhead;         // 내 앞 대기 팀 수 (ex: 0팀, 3팀)
    private Integer estimatedWaitMins;// 예상 대기 시간 (분)
    private WaitingStatus status;    // 현재 상태 (WAITING)
    private String checkInCode;          // QR 생성용 체크인 코드
    private LocalDateTime joinedAt;  // 접수 일시
    private LocalDateTime calledAt;  // 호출 일시

    public static WaitingStatusResponse of(WaitingQueue queue, Long teamsAhead, Integer estimatedMinutesPerTeam) {
        String generatedCode = String.format("CHK-%04d-%04d",
                queue.getQueueNumber() != null ? queue.getQueueNumber() : 0,
                queue.getWaitingId() != null ? queue.getWaitingId() : 0); // qr 생성을 위한 

        Integer calculatedWaitMins = (teamsAhead != null && estimatedMinutesPerTeam != null) // 둘 다 null이 아닐때
                ? (int) (teamsAhead * estimatedMinutesPerTeam) // 예상 시간 계산
                : null; // 아니면 null처리

        return WaitingStatusResponse.builder()
                .waitingId(queue.getWaitingId())
                .popId(queue.getPopupStore().getPopId())
                .queueNumber(queue.getQueueNumber())
                .peopleCount(queue.getPeopleCount())
                .status(queue.getWaitingStatus())
                .teamsAhead(teamsAhead)
                .estimatedWaitMins(calculatedWaitMins)
                .checkInCode(generatedCode)
                .joinedAt(queue.getJoinedAt())
                .calledAt(queue.getCalledAt())
                .build();
    }
}
