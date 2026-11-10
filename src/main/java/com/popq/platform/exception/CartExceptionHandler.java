package com.popq.platform.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class CartExceptionHandler {

    // 장바구니 오류 응답
    @ExceptionHandler(CartException.class)
    public ResponseEntity<Map<String, String>> handleCartException(
            CartException exception
    ) {
        Map<String, String> response = Map.of(
                "code", exception.getCode(),
                "message", exception.getMessage()
        );

        return ResponseEntity
                .status(exception.getStatus())
                .body(response);
    }
}
