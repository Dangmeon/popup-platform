package com.popq.platform.order.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode {

    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "상품은 한 개 이상이어야 합니다."),
    SALE_NOT_STARTED(HttpStatus.BAD_REQUEST, "아직 판매 시작 전인 상품입니다."),
    SALE_ENDED(HttpStatus.BAD_REQUEST, "판매가 종료된 상품입니다."),
    PURCHASE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "1인 구매 제한 수량을 초과했습니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문 정보를 찾을 수 없습니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 주문만 처리할 수 있습니다."),
    ORDER_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 취소된 주문입니다."),
    ORDER_EXPIRED(HttpStatus.CONFLICT, "이미 만료된 주문입니다."),
    STOCK_RESTORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "재고 복구 중 오류가 발생했습니다."),
    PAID_ORDER_CANCEL_REQUIRED(HttpStatus.CONFLICT, "결제 완료 주문은 결제 취소 처리가 필요합니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "처리할 수 없는 주문 상태입니다."),
    POPUP_NOT_OPEN(HttpStatus.CONFLICT, "현재 운영 중인 팝업스토어의 상품만 주문할 수 있습니다."),
    PAYMENT_PROCESSING(HttpStatus.CONFLICT, "결제 처리 중인 주문입니다. 잠시 후 다시 시도해 주세요."),
    DUPLICATE_PRODUCT(HttpStatus.BAD_REQUEST, "동일한 상품을 중복하여 주문할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}