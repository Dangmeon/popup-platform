package com.popq.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum TimeSlotErrorCode {

    TIMESLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "회차 정보를 찾을 수 없습니다."),
    TIMESLOT_OVERLAP(HttpStatus.CONFLICT, "같은 날짜에 시간이 겹치는 회차가 이미 있습니다."),
    TIMESLOT_TIME_LOCKED(HttpStatus.CONFLICT, "예약 이력이 있는 회차는 날짜와 시간을 바꿀 수 없습니다. 정원만 변경 가능합니다."),
    CAPACITY_BELOW_RESERVED(HttpStatus.CONFLICT, "정원은 현재 예약 인원보다 적을 수 없습니다."),
    TIMESLOT_HAS_RESERVATIONS(HttpStatus.CONFLICT, "예약 이력이 있는 회차는 삭제할 수 없습니다."),
    POPUP_PERIOD_MISSING(HttpStatus.CONFLICT, "팝업 운영기간이 없어 회차를 만들 수 없습니다. 팝업 정보를 먼저 수정해주세요."),
    TIMESLOT_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "회차 날짜와 정원은 필수입니다."),
    TIME_REQUIRED(HttpStatus.BAD_REQUEST, "시작 시간과 종료 시간을 입력해주세요."),
    INVALID_TIME_FORMAT(HttpStatus.BAD_REQUEST, "시간은 HH:mm 형식이어야 합니다. 예) 10:00"),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "종료 시간은 시작 시간보다 늦어야 합니다."),
    INVALID_CAPACITY(HttpStatus.BAD_REQUEST, "회차 정원은 1명 이상이어야 합니다."),
    SLOT_DATE_OUT_OF_PERIOD(HttpStatus.BAD_REQUEST, "회차 날짜는 팝업 운영기간 안이어야 합니다."),
    GENERATE_PERIOD_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "생성 기간은 팝업 운영기간 안이어야 합니다."),
    SLOT_TIME_OUT_OF_RESERV_TIME(HttpStatus.BAD_REQUEST, "회차 시간은 팝업 예약 시간 안이어야 합니다."),
    LUNCH_TIME_PAIR_REQUIRED(HttpStatus.BAD_REQUEST, "점심시간은 lunchStart, lunchEnd를 같이 입력해주세요."),
    INVALID_INTERVAL(HttpStatus.BAD_REQUEST, "회차 간격은 1분 이상이어야 합니다."),
    INVALID_BREAK_MINUTES(HttpStatus.BAD_REQUEST, "정비시간은 0분 이상이어야 합니다.");

    private final HttpStatus status;
    private final String message;

    TimeSlotErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
