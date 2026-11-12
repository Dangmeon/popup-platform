package com.popq.platform.waiting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminWaitingListResponse {

    private Long popId;
    private int totalWaitingTeams; // 현재 순수 대기 중(WAITING)인 팀 수
    private int totalCalledTeams;  // 현재 호출 중(CALLED)인 팀 수
    private List<WaitingStatusResponse> waitings; // 대기 목록 상세

    public static AdminWaitingListResponse of(
            Long popId,
            int totalWaitingTeams,
            int totalCalledTeams,
            List<WaitingStatusResponse> waitings
    ) {
        return AdminWaitingListResponse.builder()
                .popId(popId)
                .totalWaitingTeams(totalWaitingTeams)
                .totalCalledTeams(totalCalledTeams)
                .waitings(waitings)
                .build();
    }
}