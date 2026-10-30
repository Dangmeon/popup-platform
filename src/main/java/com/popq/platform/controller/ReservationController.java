package com.popq.platform.controller;

import com.popq.platform.dto.ReservationCreateRequest;
import com.popq.platform.dto.ReservationDetailResponse;
import com.popq.platform.dto.ReservationResponse;
import com.popq.platform.dto.ReservationUpdateRequest;
import com.popq.platform.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    final ReservationService reservationService;

    // 예약 생성
    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @RequestParam Long memberId,
            @RequestBody ReservationCreateRequest request
            ){
        return ResponseEntity.ok(
                reservationService.createReservation(memberId, request)
        );
    }

    // 예약 상세 조회
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationDetailResponse> getReservation(
            @RequestParam Long memberId,
            @PathVariable Long reservationId
    ) {
        return ResponseEntity.ok(
                reservationService.getReservation(memberId, reservationId)
        );
    }

    // 예약 확정
    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ReservationResponse> confirmReservation(
            @RequestParam Long memberId,
            @PathVariable Long reservationId
    ) {
        return ResponseEntity.ok(
                reservationService.confirmReservation(memberId, reservationId)
        );
    }

    // 예약 변경
    @PatchMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> updateReservation(
            @RequestParam Long memberId,
            @PathVariable Long reservationId,
            @RequestBody ReservationUpdateRequest request
    ) {
        return ResponseEntity.ok(
                reservationService.updateReservation(
                        memberId,
                        reservationId,
                        request
                )
        );
    }

    // 예약 취소
    @PatchMapping("/{reservationId}/cancel")
    public ResponseEntity<ReservationResponse> cancelReservation(
            @RequestParam Long memberId,
            @PathVariable Long reservationId
    ) {
        return ResponseEntity.ok(
                reservationService.cancelReservation(memberId, reservationId)
        );
    }

    // QR 조회
    @GetMapping(
            value = "/{reservationId}/qr",
            produces = MediaType.IMAGE_PNG_VALUE
    )
    public ResponseEntity<byte[]> getQr(
            @RequestParam Long memberId,
            @PathVariable Long reservationId
    ) {
        byte[] qrImage = reservationService.getQr(memberId, reservationId);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrImage);
    }

}
