package com.popq.platform.waiting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CheckInRequest {

    @NotBlank(message = "체크인 코드는 필수입니다.")
    private String checkInCode;
}