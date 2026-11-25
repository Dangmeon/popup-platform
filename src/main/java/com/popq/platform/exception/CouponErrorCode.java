package com.popq.platform.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CouponErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 회원입니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 쿠폰입니다."),
    USER_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 발급 쿠폰입니다."),
    COUPON_NOT_OWNER(HttpStatus.FORBIDDEN, "본인 소유의 쿠폰이 아닙니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 발급받은 쿠폰입니다."),
    COUPON_OUT_OF_STOCK(HttpStatus.CONFLICT, "쿠폰이 모두 소진되었습니다."),
    COUPON_ALREADY_USED(HttpStatus.BAD_REQUEST, "이미 사용한 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "만료된 쿠폰입니다.");

    private final HttpStatus status;
    private final String message;
}