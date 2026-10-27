package com.popq.platform.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class TimeSlotGenerateRequest {
    private Long popId;
    private LocalDate startDate;     // 비우면 팝업 운영 시작일
    private LocalDate endDate;       // 비우면 팝업 운영 종료일
    private Integer intervalMinutes; // 회차 간격(분), 비우면 60분
    private Integer capacity;        // 회차당 정원, 비우면 팝업의 reservPeopleCap
    private String startTime;        // 생성 시작 시간 "HH:mm", 비우면 팝업의 reservStart
    private String endTime;          // 생성 종료 시간 "HH:mm", 비우면 팝업의 reservEnd
    private Integer breakMinutes;    // 회차 사이 정비시간(분), 비우면 0분
    private String lunchStart;       // 점심시간 시작 "HH:mm", 비우면 점심시간 없음
    private String lunchEnd;        // 점심시간 끝 "HH:mm", lunchStart와 같이 입력
}
