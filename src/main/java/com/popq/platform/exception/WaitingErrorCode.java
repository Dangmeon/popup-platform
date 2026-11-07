package com.popq.platform.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WaitingErrorCode {

    // 404 NOT_FOUND
    WAITING_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 waitingId의 대기 내역을 찾을 수 없습니다."),
    POPUP_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 팝업 스토어가 존재하지 않습니다."),

    // 401

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),

    // 400 BAD_REQUEST

    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "현재 상태에서는 처리할 수 없는 요청입니다."),
    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "잘못된 전화번호 형식입니다."),
    INVALID_PEOPLE_COUNT(HttpStatus.BAD_REQUEST, "인원수는 최소 1명 이상이어야 합니다."),
    ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "이미 취소된 대기입니다."),
    ALREADY_CALLED(HttpStatus.BAD_REQUEST, "이미 호출된 대기입니다."),
    ALREADY_ENTERED(HttpStatus.BAD_REQUEST, "이미 입장된 대기 입니다."),
    ALREADY_EXPIRED(HttpStatus.BAD_REQUEST, "이미 만료처리된 대기 입니다."),
    CHECK_IN_EXPIRED(HttpStatus.BAD_REQUEST, "입장 가능 시간이 초과되었습니다."),
    INVALID_STATUS_FOR_CALL(HttpStatus.BAD_REQUEST, "대기 중 상태일 때만 호출할 수 있습니다."),
    NOT_CALLED_YET(HttpStatus.BAD_REQUEST, "호출 상태인 대기 팀만 처리할 수 있습니다."),

    // 409 CONFLICT
    ALREADY_WAITING(HttpStatus.CONFLICT, "이미 해당 팝업스토어에 대기 등록된 연락처입니다."),
    POPUP_NOT_OPEN(HttpStatus.CONFLICT, "현재 대기 접수 시간이 아닙니다."),
    WAITING_CLOSED(HttpStatus.CONFLICT, "현장 대기 접수가 마감되었습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}