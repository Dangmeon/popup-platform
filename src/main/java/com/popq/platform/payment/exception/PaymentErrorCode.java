package com.popq.platform.payment.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode {

    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "결제할 수 없는 상태입니다."),
    TOSS_PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "결제 승인에 실패했습니다."),
    TOSS_CANCEL_FAILED(HttpStatus.BAD_REQUEST, "결제 취소에 실패했습니다."),
    TOSS_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "토스페이먼츠 서버 오류가 발생했습니다."),
    PAYMENT_KEY_MISMATCH(HttpStatus.CONFLICT, "기존 결제 정보와 결제 키가 일치하지 않습니다."),
    TOSS_PAYMENT_LOOKUP_FAILED(HttpStatus.BAD_GATEWAY, "외부 결제 정보 조회에 실패했습니다."),
    INVALID_TOSS_PAYMENT_RESPONSE(HttpStatus.BAD_GATEWAY, "외부 결제 응답 정보가 올바르지 않습니다."),
    PAYMENT_OPERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 처리 이력을 찾을 수 없습니다."),
    PAYMENT_OPERATION_MISMATCH(HttpStatus.CONFLICT, "기존 결제 처리 요청 정보와 일치하지 않습니다."),
    PAYMENT_CONNECTION_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "결제 서버와 통신 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}