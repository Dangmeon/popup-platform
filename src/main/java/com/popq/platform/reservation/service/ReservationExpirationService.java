package com.popq.platform.reservation.service;

import com.popq.platform.entity.Reservation;
import com.popq.platform.entity.ReservationStatus;
import com.popq.platform.entity.TimeSlot;
import com.popq.platform.reservation.exception.ReservationErrorCode;
import com.popq.platform.reservation.exception.ReservationException;
import com.popq.platform.reservation.repository.ReservationRepository;
import com.popq.platform.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservationExpirationService {

    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;

    @Transactional
    public void expireReservation(Long reservationId) {

        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new ReservationException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if(reservation.getReservationStatus() != ReservationStatus.HOLD){
            return;
        }

        if(reservation.getExpiresAt().isAfter(LocalDateTime.now())){
            return;
        }

        Long timeSlotId = reservation.getTimeSlot().getTimeSlotId();
        TimeSlot timeSlot = timeSlotRepository.findByIdForUpdate(timeSlotId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회차가 없습니다."));

        timeSlot.decreaseReservedCount(reservation.getPeopleCount());

        reservation.expire();
    }

}
