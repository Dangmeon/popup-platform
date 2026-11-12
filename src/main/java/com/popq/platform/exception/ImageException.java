package com.popq.platform.exception;

import lombok.Getter;

@Getter
public class ImageException extends RuntimeException {
    private final ImageErrorCode errorCode;

    public ImageException(ImageErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}