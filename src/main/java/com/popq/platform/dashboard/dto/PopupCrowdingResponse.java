package com.popq.platform.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class PopupCrowdingResponse { // 대시보드 : 팝업별 혼잡도 (오늘 회차가 있는 팝업)
    private Long popId;
    private String title;
    private int percent;     // 오늘 예약 인원 / 오늘 정원 x 100
    private long waiting;    // 현재 웨이팅 수
}
