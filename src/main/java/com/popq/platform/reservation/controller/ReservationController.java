package com.popq.platform.reservation.controller;

import com.popq.platform.reservation.dto.ReservationCreateRequest;
import com.popq.platform.reservation.dto.ReservationDetailResponse;
import com.popq.platform.reservation.dto.ReservationResponse;
import com.popq.platform.reservation.dto.ReservationUpdateRequest;
import com.popq.platform.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    final ReservationService reservationService;

    // 예약 생성
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            Authentication authentication,
            @Valid @RequestBody ReservationCreateRequest request
            ){
        String username = authentication.getName();

        return ResponseEntity.ok(
                reservationService.createReservation(username, request)
        );
    }

    // 예약 상세 조회
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationDetailResponse> getReservation(
            Authentication authentication,
            @PathVariable Long reservationId
    ) {
        String username = authentication.getName();

        return ResponseEntity.ok(
                reservationService.getReservation(username, reservationId)
        );
    }

    // 예약 확정
    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ReservationResponse> confirmReservation(
            Authentication authentication,
            @PathVariable Long reservationId
    ) {
        String username = authentication.getName();

        return ResponseEntity.ok(
                reservationService.confirmReservation(username, reservationId)
        );
    }

    // 예약 변경
    @PatchMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> updateReservation(
            Authentication authentication,
            @PathVariable Long reservationId,
            @Valid @RequestBody ReservationUpdateRequest request
    ) {
        String username = authentication.getName();

        return ResponseEntity.ok(
                reservationService.updateReservation(
                        username,
                        reservationId,
                        request
                )
        );
    }

    // 예약 취소
    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(
            Authentication authentication,
            @PathVariable Long reservationId
    ) {
        String username = authentication.getName();

        return ResponseEntity.ok(
                reservationService.cancelReservation(username, reservationId)
        );
    }

    // QR 조회
    @GetMapping(
            value = "/{reservationId}/qr",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> getQr(
            Authentication authentication,
            @PathVariable Long reservationId
    ) {
        String username = authentication.getName();

        byte[] qrImage = reservationService.getQr(username, reservationId);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrImage);
    }

}
