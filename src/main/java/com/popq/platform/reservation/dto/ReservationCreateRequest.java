package com.popq.platform.reservation.dto;

public record ReservationCreateRequest(
        Long timeSlotId,
        int peopleCount
){
}
