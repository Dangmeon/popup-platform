package com.popq.platform.qna.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 클라이언트로부터 요청받을 답변 데이터 규격 제약 내용 2000자

@Getter
@NoArgsConstructor
public class QnaAnswerRequest {

    @NotBlank(message = "답변 내용을 입력해주세요.")
    @Size(max = 2000, message = "답변은 2000자 이내로 입력해주세요.")
    private String answerContent;
}