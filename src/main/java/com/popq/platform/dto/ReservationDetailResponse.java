package com.popq.platform.dto;

import com.popq.platform.entity.PopupStore;
import com.popq.platform.entity.Reservation;
import com.popq.platform.entity.ReservationStatus;
import com.popq.platform.entity.TimeSlot;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationDetailResponse(
        Long reservationId,
        int peopleCount,
        ReservationStatus reservationStatus,
        LocalDateTime expiresAt,
        String qrToken,

        Long timeSlotId,
        LocalDate slotDate,
        String startTime,
        String endTime,

        Long popId,
        String popupName,
        String popupImage
) {

    public static ReservationDetailResponse fromDetailReservation(Reservation reservation) {
        TimeSlot timeSlot = reservation.getTimeSlot();
        PopupStore popupStore = timeSlot.getPopupStore();

        return new ReservationDetailResponse(
                reservation.getReservationId(),
                reservation.getPeopleCount(),
                reservation.getReservationStatus(),
                reservation.getExpiresAt(),
                reservation.getQrToken(),

                timeSlot.getTimeSlotId(),
                timeSlot.getSlotDate(),
                timeSlot.getStartTime(),
                timeSlot.getEndTime(),

                popupStore.getPopId(),
                popupStore.getTitle(),
                popupStore.getStoreImg()
        );
    }
}
