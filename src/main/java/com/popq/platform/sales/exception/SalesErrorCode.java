package com.popq.platform.sales.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum SalesErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 회원을 찾을 수 없습니다."),
    POPUP_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 팝업 스토어를 찾을 수 없습니다."),
    FORBIDDEN_ADMIN_ONLY(HttpStatus.FORBIDDEN, "전체 매출은 관리자만 조회할 수 있습니다."),
    POPUP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "담당 팝업의 매출만 조회할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    SalesErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}