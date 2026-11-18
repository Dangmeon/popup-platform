package com.popq.platform.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ImageErrorCode {

    IMAGE_EMPTY(HttpStatus.BAD_REQUEST, "업로드할 이미지 파일이 없습니다."),
    INVALID_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "이미지는 jpg, jpeg, png, webp, gif 형식만 업로드할 수 있습니다."),
    IMAGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "이미지는 5MB 이하만 업로드할 수 있습니다."),
    IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 저장 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ImageErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}