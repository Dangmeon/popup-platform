package com.popq.platform.exception;

public enum ReservationErrorCode {

    NOT_FOUND("예약 정보를 찾을 수 없습니다."),
    DUPLICATE("이미 해당 타임슬롯에 예약이 존재합니다."),
    CAPACITY_EXCEEDED("예약 가능한 잔여 인원이 부족합니다."),
    EXPIRED("만료된 예약입니다."),
    ACCESS_DENIED("해당 예약에 접근할 권한이 없습니다.");

    private final String message;

    ReservationErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
