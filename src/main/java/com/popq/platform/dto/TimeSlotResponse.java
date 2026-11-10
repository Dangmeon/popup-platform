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
        // 값이 비어 있는 데이터(SQL로 직접 넣은 행 등)가 있어도 목록 전체가 500이 나지 않도록 계산값은 null로 둠
        Integer remaining = null;
        if (entity.getCapacity() != null && entity.getReservedCount() != null) {
            remaining = entity.getCapacity() - entity.getReservedCount();
        }
        LocalDateTime reservOpenAt = null;
        LocalDateTime reservCloseAt = null;
        if (entity.getSlotDate() != null) {
            reservOpenAt = entity.getSlotDate().minusDays(TimeSlot.RESERV_OPEN_DAYS_BEFORE).atTime(TimeSlot.RESERV_OPEN_TIME);
            reservCloseAt = entity.getSlotDate().atStartOfDay();
        }

        return TimeSlotResponse.builder()
                .timeSlotId(entity.getTimeSlotId())
                .popId(entity.getPopupStore().getPopId())
                .slotNum(entity.getSlotNum())
                .slotDate(entity.getSlotDate())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .capacity(entity.getCapacity())
                .reservedCount(entity.getReservedCount())
                .remaining(remaining)
                .reservOpenAt(reservOpenAt)
                .reservCloseAt(reservCloseAt)
                .build();
    }
}
