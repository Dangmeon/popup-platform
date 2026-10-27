package com.popq.platform.dto;

import com.popq.platform.entity.TimeSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class TimeSlotResponse {
    private Long timeSlotId;
    private Long popId;
    private Integer slotNum;
    private LocalDate slotDate;
    private String startTime;
    private String endTime;
    private Integer capacity;
    private Integer reservedCount;
    private Integer remaining; // 남은 자리 = 정원 - 예약 인원 (화면 표시용)
    private LocalDateTime reservOpenAt;  // 예약 오픈 일시 (계산값)
    private LocalDateTime reservCloseAt; // 예약 마감 일시 (계산값, 이 시각부터 예약 불가)

    // Entity -> Response 변환시 사용
    public static TimeSlotResponse toDto(TimeSlot entity) {
        return TimeSlotResponse.builder()
            .timeSlotId(entity.getTimeSlotId())
            .popId(entity.getPopupStore().getPopId())
            .slotNum(entity.getSlotNum())
            .slotDate(entity.getSlotDate())
            .startTime(entity.getStartTime())
            .endTime(entity.getEndTime())
            .capacity(entity.getCapacity())
            .reservedCount(entity.getReservedCount())
            .remaining(entity.getCapacity() - entity.getReservedCount())
            .reservOpenAt(entity.getSlotDate().minusDays(TimeSlot.RESERV_OPEN_DAYS_BEFORE).atTime(TimeSlot.RESERV_OPEN_TIME))
            .reservCloseAt(entity.getSlotDate().atStartOfDay())
            .build();
    }
}
