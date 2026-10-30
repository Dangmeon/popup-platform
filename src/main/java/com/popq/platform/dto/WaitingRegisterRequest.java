package com.popq.platform.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WaitingRegisterRequest {

    @NotNull(message = "팝업스토어 ID는 필수입니다.") // 팝업 스토어 아이디는 NOT NULL이므로
    private Long popId;

    private Long memberId;

    @NotBlank(message = "연락처는 필수입니다.") // 연락처는 NOT NULL이므로
    @Pattern(
            regexp = "^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$", // 01N(지역번호) - 000 or 0000[집 / 휴대폰] - 0000 패턴
            message = "올바른 전화 번호 형식을 입력해주세요. (ex: 010-1234-5678)"
    )
    private String phone;

    @Min(value = 1, message = "인원수는 최소 1명 이상이어야 합니다.") // DB 조건에 의해 1명 이상 입력 받기
    private int peopleCount;
}