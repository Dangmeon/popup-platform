package com.popq.platform.exception;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ErrorResponse {
    private final String code;
    private final String message;
    private final Map<String, String> errors;
}
