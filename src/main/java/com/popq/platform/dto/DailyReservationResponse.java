package com.popq.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;


@Getter
@AllArgsConstructor
@Builder
public class DailyReservationResponse { // 대시보드 : 날짜별 예약 · 입장 수 (최근 7일 추이 그래프)
    private LocalDate date;
    private String day;      // 요일 (월, 화, ...)
    private long reserved;   // 예약 수 (CONFIRMED, USED)
    private long entered;    // 입장 수 (USED)
}
