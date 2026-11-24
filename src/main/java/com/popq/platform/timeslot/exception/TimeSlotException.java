package com.popq.platform.timeslot.exception;

import lombok.Getter;

@Getter
public class TimeSlotException extends RuntimeException {
    private final TimeSlotErrorCode errorCode;

    public TimeSlotException(TimeSlotErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
