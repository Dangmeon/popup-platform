package com.popq.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AuthErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "비밀번호가 일치하지 않습니다."),
    USER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 회원입니다."),
    ALREADY_WITHDRAWN(HttpStatus.BAD_REQUEST, "이미 탈퇴 처리된 회원입니다."),
    USER_INFO_MISMATCH(HttpStatus.BAD_REQUEST, "이메일과 이름이 일치하지 않습니다."),
    VERIFICATION_CODE_NOT_FOUND(HttpStatus.BAD_REQUEST, "인증 코드를 요청하지 않았거나 만료되었습니다."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다. 다시 요청해 주세요."),
    INVALID_VERIFICATION_CODE(HttpStatus.BAD_REQUEST, "인증 코드가 일치하지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}