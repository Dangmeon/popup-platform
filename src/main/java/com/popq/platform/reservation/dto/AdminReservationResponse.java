package com.popq.platform.reservation.dto;

import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class AdminReservationResponse {

    private Long reservationId;

    private Long popId;
    private String title;

    private Long timeSlotId;
    private LocalDate slotDate;
    private String startTime;

    private Long memberId;
    private String username;

    private int peopleCount;
    private ReservationStatus reservationStatus;
    private LocalDateTime reservedAt;

    public static AdminReservationResponse from(Reservation reservation) {
        return new AdminReservationResponse(
                reservation.getReservationId(),
                reservation.getTimeSlot().getPopupStore().getPopId(),
                reservation.getTimeSlot().getPopupStore().getTitle(),
                reservation.getTimeSlot().getTimeSlotId(),
                reservation.getTimeSlot().getSlotDate(),
                reservation.getTimeSlot().getStartTime(),
                reservation.getUser().getId(),
                reservation.getUser().getUsername(),
                reservation.getPeopleCount(),
                reservation.getReservationStatus(),
                reservation.getReservedAt()
        );
    }
}
