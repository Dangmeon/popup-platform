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
    private Integer queueNumber;     // 당일 대기 번호 (ex: 1번, 2번)
    private long teamsAhead;         // 내 앞 대기 팀 수 (ex: 0팀, 3팀)
    private int estimatedWaitMinutes;// 예상 대기 시간 (분)
    private WaitingStatus status;    // 현재 상태 (WAITING)
    private LocalDateTime joinedAt;  // 접수 일시

    public static WaitingStatusResponse of(WaitingQueue queue, long teamsAhead, int estimatedMinutesPerTeam) {
        return WaitingStatusResponse.builder()
                .waitingId(queue.getWaitingId())
               // .popId(queue.getPopupStore().getPopId()) -- 팝업 객체 생성 시 주석 해제
                .queueNumber(queue.getQueueNumber())
                .teamsAhead(teamsAhead)
                .estimatedWaitMinutes((int) (teamsAhead * estimatedMinutesPerTeam)) // (예상 대기 시간) = (팀당 평균 체류 시간) * (남은 팀 수)
                .status(queue.getWaitingStatus())
                .joinedAt(queue.getJoinedAt())
                .build();
    }
}
