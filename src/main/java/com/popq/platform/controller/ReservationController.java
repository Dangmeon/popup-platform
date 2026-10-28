package com.popq.platform.controller;

import com.popq.platform.dto.ReservationCreateRequest;
import com.popq.platform.dto.ReservationResponse;
import com.popq.platform.dto.ReservationUpdateRequest;
import com.popq.platform.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservation")
@RequiredArgsConstructor
public class ReservationController {

    final ReservationService reservationService;

    // 예약 생성
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @RequestBody ReservationCreateRequest request
            ){
        return null;
    }

    // 예약 상세 조회
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservation(
            @PathVariable Long reservationId
    ) {
        return null;
    }

    // 예약 확정
    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ReservationResponse> confirmReservation(
            @PathVariable Long reservationId
    ) {
        return null;
    }

    // 예약 변경
    @PatchMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> updateReservation(
            @PathVariable Long reservationId,
            @RequestBody ReservationUpdateRequest request
    ) {
        return null;
    }

    // 예약 취소
    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(
            @PathVariable Long reservationId
    ) {
        return null;
    }

    // QR 조회
    @GetMapping(
            value = "/{reservationId}/qr",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> getQr(
            @PathVariable Long reservationId
    ) {
        return null;
    }

}
