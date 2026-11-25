package com.popq.platform.sales.exception;

import lombok.Getter;

@Getter
public class SalesException extends RuntimeException{

    private final SalesErrorCode errorCode;

    public SalesException(SalesErrorCode errorCode){
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
