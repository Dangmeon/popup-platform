package com.popq.platform.qna.exception;

import lombok.Getter;

@Getter
public class QnaException extends RuntimeException {

    private final QnaErrorCode errorCode;

    public QnaException(QnaErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}