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
        return AdminReservationResponse.builder()
                .reservationId(reservation.getReservationId())
                .popId(reservation.getTimeSlot().getPopupStore().getPopId())
                .title(reservation.getTimeSlot().getPopupStore().getTitle())
                .timeSlotId(reservation.getTimeSlot().getTimeSlotId())
                .slotDate(reservation.getTimeSlot().getSlotDate())
                .startTime(reservation.getTimeSlot().getStartTime())
                .memberId(reservation.getUser().getId())
                .username(reservation.getUser().getUsername())
                .peopleCount(reservation.getPeopleCount())
                .reservationStatus(reservation.getReservationStatus())
                .reservedAt(reservation.getReservedAt())
                .build();
    }
}
