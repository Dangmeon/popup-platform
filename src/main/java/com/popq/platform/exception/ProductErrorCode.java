package com.popq.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ProductErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 정보를 찾을 수 없습니다."),
    PRODUCT_HAS_ORDERS(HttpStatus.CONFLICT, "주문 내역이 있는 상품은 삭제할 수 없습니다."),
    STOCK_DECREASE_NOT_ALLOWED(HttpStatus.CONFLICT, "판매가 시작된 상품은 재고를 추가만 할 수 있습니다."),
    STOCK_SHORTAGE(HttpStatus.CONFLICT, "현재 재고보다 많이 차감할 수 없습니다."),
    SALE_START_LOCKED(HttpStatus.CONFLICT, "판매가 시작된 상품은 판매 시작일시를 변경할 수 없습니다."),
    INVALID_STOCK(HttpStatus.BAD_REQUEST, "재고는 0개 이상 입력해주세요."),
    INVALID_STOCK_QUANTITY(HttpStatus.BAD_REQUEST, "조정 수량을 입력해주세요. 예) 10 (추가) / -5 (차감)"),
    PRODUCT_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "상품명은 필수입니다."),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "가격은 0원 이상 입력해주세요."),
    INVALID_MAX_QTY(HttpStatus.BAD_REQUEST, "1인 구매 제한은 1개 이상이어야 합니다."),
    SALE_PERIOD_REQUIRED(HttpStatus.BAD_REQUEST, "판매 시작일시와 종료일시는 필수입니다."),
    INVALID_SALE_START(HttpStatus.BAD_REQUEST, "판매 시작은 팝업 종료일 다음날부터 가능합니다."),
    INVALID_SALE_PERIOD(HttpStatus.BAD_REQUEST, "판매 종료는 판매 시작보다 늦어야 합니다."),
    INVALID_SALE_END(HttpStatus.BAD_REQUEST, "판매 종료일시는 현재 시각 이후로만 변경할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    ProductErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
