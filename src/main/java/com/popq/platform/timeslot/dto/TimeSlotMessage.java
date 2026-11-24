package com.popq.platform.timeslot.dto;

// WebSocket을 통해 프론트에 실제로 전달할 메시지 DTO
public record TimeSlotMessage(
        Long timeSlotId,
        int capacity,
        int reservedCount,
        int remainingCount,
        boolean closed
) {
}
