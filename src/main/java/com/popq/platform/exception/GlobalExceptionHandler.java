package com.popq.platform.exception;

import com.popq.platform.reservation.exception.ReservationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. DTO 유효성 검증(@Valid) 실패 시 발생하는 에러 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    // 2. 비즈니스 로직 예외 처리
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage()); // 프론트엔드가 'message'로 읽을 때 대응
        error.put("error", ex.getMessage());   // 기존 호환용
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // 3. 데이터베이스 제약 조건 위반 처리
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        Map<String, String> error = new HashMap<>();
        String errorMessage = "이미 사용 중인 정보(이메일 또는 전화번호 등)입니다.";

        error.put("message", errorMessage);
        error.put("error", errorMessage);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // 예약 에러 처리
    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<Map<String, String>> handleReservationException(ReservationException ex) {
        Map<String, String> error = new HashMap<>();

        error.put("code", ex.getErrorCode().name());
        error.put("message", ex.getErrorCode().getMessage());

        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(error);

    }

    // 팝업 에러 처리
    @ExceptionHandler(PopupException.class)
    public ResponseEntity<Map<String, String>> handlePopupException(PopupException ex) {
        Map<String, String> error = new HashMap<>();

        error.put("code", ex.getErrorCode().name());
        error.put("message", ex.getErrorCode().getMessage());

        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(error);
    }

    // 회차 에러 처리
    @ExceptionHandler(TimeSlotException.class)
    public ResponseEntity<Map<String, String>> handleTimeSlotException(TimeSlotException ex) {
        Map<String, String> error = new HashMap<>();

        error.put("code", ex.getErrorCode().name());
        error.put("message", ex.getErrorCode().getMessage());

        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(error);
    }

    // 상품 에러 처리
    @ExceptionHandler(ProductException.class)
    public ResponseEntity<Map<String, String>> handleProductException(ProductException ex) {
        Map<String, String> error = new HashMap<>();

        error.put("code", ex.getErrorCode().name());
        error.put("message", ex.getErrorCode().getMessage());

        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(error);
    }

    // 결제 에러 처리
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<Map<String, String>> handlePaymentException(PaymentException ex) {
        Map<String, String> error = new HashMap<>();

        error.put("code", ex.getCode());
        error.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        Map<String, String> error = new HashMap<>();

        error.put("code", "INVALID_STATE");
        error.put("message", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<Map<String, String>> handleOrderException(OrderException ex) {
        Map<String, String> error = new HashMap<>();

        error.put("code", ex.getErrorCode().name());
        error.put("message", ex.getMessage());

        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(error);
    }

}