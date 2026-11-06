package com.popq.platform.exception;

import lombok.Getter;

@Getter
public class PopupException extends RuntimeException {
    private final PopupErrorCode errorCode;

    public PopupException(PopupErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
