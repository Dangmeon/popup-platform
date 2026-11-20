package com.popq.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum PopupErrorCode {

    POPUP_NOT_FOUND(HttpStatus.NOT_FOUND, "팝업스토어 정보를 찾을 수 없습니다."),
    POPUP_HAS_TIMESLOTS(HttpStatus.CONFLICT, "회차가 등록된 팝업은 삭제할 수 없습니다. 회차를 먼저 삭제해주세요."),
    POPUP_HAS_PRODUCTS(HttpStatus.CONFLICT, "상품이 등록된 팝업은 삭제할 수 없습니다. 상품을 먼저 삭제해주세요."),
    POPUP_REQUIRED_FIELD(HttpStatus.BAD_REQUEST, "팝업명, 장소, 운영 시작일, 종료일, 예약 시작·종료 시간은 필수입니다."),
    INVALID_POPUP_PERIOD(HttpStatus.BAD_REQUEST, "종료일은 시작일보다 빠를 수 없습니다."),
    INVALID_RESERV_TIME_FORMAT(HttpStatus.BAD_REQUEST, "예약 시작·종료 시간은 HH:mm 형식이어야 합니다. 예) 10:00"),
    INVALID_RESERV_TIME_RANGE(HttpStatus.BAD_REQUEST, "예약 종료 시간은 시작 시간보다 늦어야 합니다."),
    POPUP_TIMESLOT_OUT_OF_PERIOD(HttpStatus.CONFLICT, "변경할 운영기간 밖에 회차가 있습니다. 해당 회차를 먼저 삭제하거나 수정해주세요."),
    POPUP_PRODUCT_SALE_CONFLICT(HttpStatus.CONFLICT, "팝업 종료일은 등록된 상품의 판매 시작일보다 앞이어야 합니다. 상품 판매기간을 먼저 수정해주세요."),
    POPUP_TIMESLOT_OUT_OF_RESERV_TIME(HttpStatus.CONFLICT, "변경할 예약 시간 밖에 회차가 있습니다. 해당 회차를 먼저 삭제하거나 수정해주세요."),
    INVALID_RESERV_PEOPLE_CAP(HttpStatus.BAD_REQUEST, "예약 정원은 1명 이상이어야 합니다."),
    INVALID_ROUND_NUM(HttpStatus.BAD_REQUEST, "팝업 오픈 차수는 1 이상이어야 합니다."),
    POPUP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "담당 팝업만 관리할 수 있습니다."),
    ADMIN_ONLY(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있는 기능입니다."),
    MANAGER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 이메일의 회원을 찾을 수 없습니다."),
    NOT_MANAGER_ROLE(HttpStatus.BAD_REQUEST, "팝업 매니저(MANAGER) 회원만 담당자로 지정할 수 있습니다."),

    // 팀장 작성 --------- 팝업 상태 관리 관련 ErrorCode
    POPUP_CANCELLED_MODIFICATION_DENIED(HttpStatus.CONFLICT, "종료된 팝업은 수정하거나 하위 회차·상품을 변경할 수 없습니다."),
    POPUP_ALREADY_PAUSED(HttpStatus.CONFLICT, "이미 일시 중단된 팝업입니다."),
    POPUP_NOT_PAUSED(HttpStatus.CONFLICT, "일시 중단 상태인 팝업만 재개할 수 있습니다."),
    POPUP_ALREADY_CANCELLED(HttpStatus.CONFLICT, "이미 종료(취소)된 팝업입니다.");

    private final HttpStatus status;
    private final String message;

    PopupErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
