package com.popq.platform.dto;

public record ReservationCreateRequest(
        Long timeSlotId,
        int peopleCount
){
}
