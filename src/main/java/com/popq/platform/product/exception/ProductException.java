package com.popq.platform.product.exception;

import lombok.Getter;

@Getter
public class ProductException extends RuntimeException {
    private final com.popq.platform.product.exception.ProductErrorCode errorCode;

    public ProductException(com.popq.platform.product.exception.ProductErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
