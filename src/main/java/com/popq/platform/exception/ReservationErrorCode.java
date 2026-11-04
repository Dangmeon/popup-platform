package com.popq.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ReservationErrorCode {

    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약 정보를 찾을 수 없습니다."),
    DUPLICATE_RESERVATION(HttpStatus.CONFLICT, "이미 예약된 시간입니다."),
    CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "예약 가능한 인원을 초과했습니다."),
    INVALID_PEOPLE_COUNT(HttpStatus.BAD_REQUEST, "예약 인원은 1명 이상 3명 이하여야 합니다."),
    TIMESLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "회차 정보를 찾을 수 없습니다."),
    RESERVATION_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "현재 예약 가능한 시간이 아닙니다."),
    INVALID_RESERVATION_STATUS(HttpStatus.CONFLICT, "현재 예약 상태에서는 해당 작업을 수행할 수 없습니다."),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT, "예약 유효 시간이 만료되었습니다."),
    CANCELLATION_DEADLINE_PASSED(HttpStatus.CONFLICT, "취소 가능 시간이 지났습니다."),
    QR_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "QR 정보를 찾을 수 없습니다."),
    QR_NOT_AVAILABLE(HttpStatus.CONFLICT, "현재 예약 상태에서는 QR을 사용할 수 없습니다."),
    QR_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "QR 코드 생성에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    ReservationErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}