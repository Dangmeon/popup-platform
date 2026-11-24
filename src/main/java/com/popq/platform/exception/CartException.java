package com.popq.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CartException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public CartException(
            HttpStatus status,
            String code,
            String message
    ) {
        super(message);
        this.status = status;
        this.code = code;
    }
}