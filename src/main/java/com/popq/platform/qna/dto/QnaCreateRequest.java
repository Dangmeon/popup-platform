package com.popq.platform.qna.dto;

import com.popq.platform.qna.entity.QnaCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 클라이언트로부터 요청받을 질문 데이터 규격 제약 카테고리 필수 / 제목 200자 / 내용 2000자 / 비밀글 여부

@Getter
@NoArgsConstructor
public class QnaCreateRequest {

    @NotNull(message = "문의 카테고리를 선택해주세요.")
    private QnaCategory category;

    @NotBlank(message = "문의 제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자 이내로 입력해주세요.")
    private String title;

    @NotBlank(message = "문의 내용은 필수입니다.")
    @Size(max = 2000, message = "내용은 2000자 이내로 입력해주세요.")
    private String content;

    private boolean secret;
}