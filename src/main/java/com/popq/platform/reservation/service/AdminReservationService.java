package com.popq.platform.reservation.service;

import com.popq.platform.reservation.dto.AdminReservationResponse;
import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.repository.ReservationRepository;
import com.popq.platform.service.PopupAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReservationService {

    private final PopupAccessService popupAccessService;
    private final ReservationRepository reservationRepository;

    public List<AdminReservationResponse> getReservations(
            String username, Long popId, ReservationStatus status
    ){

        popupAccessService.checkPopupAccess(popId, username);

        List<Reservation> reservations;

        if(status == null){
            reservations = reservationRepository.findByPopupId(popId);
        }else {
            reservations = reservationRepository.findByPopupIdAndStatus(popId, status);
        }

        return reservations.stream()
                .map(AdminReservationResponse::from)
                .toList();
    }
}
