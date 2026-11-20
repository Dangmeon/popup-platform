package com.popq.platform.qna.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QnaErrorCode {

    // 400 BAD_REQUEST
    ANSWERED_QNA_CANNOT_BE_MODIFIED(HttpStatus.BAD_REQUEST, "답변이 완료된 문의는 수정할 수 없습니다."),
    INVALID_QNA_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 QnA 요청입니다."),

    // 401 UNAUTHORIZED / 403 FORBIDDEN
    UNAUTHORIZED_QNA_ACCESS(HttpStatus.UNAUTHORIZED, "로그인이 필요한 서비스입니다."),
    FORBIDDEN_SECRET_QNA(HttpStatus.FORBIDDEN, "작성자와 관리자만 열람할 수 있는 비밀글입니다."),
    FORBIDDEN_QNA_MODIFICATION(HttpStatus.FORBIDDEN, "본인이 작성한 문의만 수정할 수 있습니다."),
    FORBIDDEN_QNA_DELETION(HttpStatus.FORBIDDEN, "본인이 작성한 문의 또는 관리자만 삭제할 수 있습니다."),
    FORBIDDEN_ADMIN_ONLY(HttpStatus.FORBIDDEN, "관리자만 답변을 등록하거나 수정할 수 있습니다."),

    // 404 NOT_FOUND
    QNA_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 문의글을 찾을 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}