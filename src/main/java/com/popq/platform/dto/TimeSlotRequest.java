package com.popq.platform.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class TimeSlotRequest {
    private Integer slotNum;
    private LocalDate slotDate;
    private String startTime;
    private String endTime;
    private Integer capacity;
}
