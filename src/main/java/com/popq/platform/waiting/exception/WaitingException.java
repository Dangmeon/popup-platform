package com.popq.platform.waiting.exception;

import com.popq.platform.waiting.exception.WaitingErrorCode;
import com.popq.platform.waiting.exception.WaitingException;
import lombok.Getter;

@Getter
public class WaitingException extends RuntimeException {

    private final WaitingErrorCode errorCode;

    public WaitingException(WaitingErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // + 상세 메시지
    public WaitingException(WaitingErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}