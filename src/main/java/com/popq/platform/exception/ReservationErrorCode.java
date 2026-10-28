package com.popq.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ReservationErrorCode {

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 정보를 찾을 수 없습니다."),
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT, "이미 예약된 시간입니다."),
    CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "예약 가능한 인원을 초과했습니다."),
    INVALID_PEOPLE_COUNT(HttpStatus.BAD_REQUEST, "예약 인원은 1명 이상이어야 합니다.");

    private final HttpStatus status;
    private final String message;

    ReservationErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
