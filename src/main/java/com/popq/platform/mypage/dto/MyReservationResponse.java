package com.popq.platform.mypage.dto;

import com.popq.platform.reservation.entity.Reservation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MyReservationResponse {

    private Long reservationId;
    private Long popId;
    private String popTitle;
    private LocalDate reservationDate;
    private String startTime;
    private String endTime;
    private int peopleCount;
    private String status;

    public static MyReservationResponse from(Reservation reservation) {
        return MyReservationResponse.builder()
                .reservationId(reservation.getReservationId())
                .popId(reservation.getTimeSlot().getPopupStore().getPopId())
                .popTitle(reservation.getTimeSlot().getPopupStore().getTitle())
                .reservationDate(reservation.getTimeSlot().getSlotDate())
                .startTime(reservation.getTimeSlot().getStartTime())
                .endTime(reservation.getTimeSlot().getEndTime())
                .peopleCount(reservation.getPeopleCount())
                .status(reservation.getReservationStatus().name())
                .build();
    }
}