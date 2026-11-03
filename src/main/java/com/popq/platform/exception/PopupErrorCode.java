package com.popq.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PopupErrorCode {

    POPUP_NOT_FOUND(HttpStatus.NOT_FOUND, "팝업스토어 정보를 찾을 수 없습니다."),
    POPUP_HAS_TIMESLOTS(HttpStatus.CONFLICT, "회차가 등록된 팝업은 삭제할 수 없습니다. 회차를 먼저 삭제해주세요."),
    POPUP_HAS_PRODUCTS(HttpStatus.CONFLICT, "상품이 등록된 팝업은 삭제할 수 없습니다. 상품을 먼저 삭제해주세요."),
    POPUP_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "팝업명, 장소, 운영 시작일, 종료일은 필수입니다."),
    INVALID_WALKIN_STATUS(HttpStatus.BAD_REQUEST, "웨이팅 상태는 OPEN, PAUSED, CLOSED 중 하나여야 합니다."),
    INVALID_POPUP_PERIOD(HttpStatus.BAD_REQUEST, "종료일은 시작일보다 빠를 수 없습니다."),
    INVALID_RESERV_TIME_FORMAT(HttpStatus.BAD_REQUEST, "예약 시작·종료 시간은 HH:mm 형식이어야 합니다. 예) 10:00"),
    INVALID_RESERV_TIME_RANGE(HttpStatus.BAD_REQUEST, "예약 종료 시간은 시작 시간보다 늦어야 합니다."),
    POPUP_TIMESLOT_OUT_OF_PERIOD(HttpStatus.CONFLICT, "변경할 운영기간 밖에 회차가 있습니다. 해당 회차를 먼저 삭제하거나 수정해주세요."),
    POPUP_PRODUCT_SALE_CONFLICT(HttpStatus.CONFLICT, "팝업 종료일은 등록된 상품의 판매 시작일보다 앞이어야 합니다. 상품 판매기간을 먼저 수정해주세요.");

    private final HttpStatus status;
    private final String message;

    PopupErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
