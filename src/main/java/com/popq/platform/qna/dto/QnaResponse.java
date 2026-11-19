package com.popq.platform.qna.dto;

import com.popq.platform.qna.entity.Qna;
import com.popq.platform.qna.entity.QnaCategory;
import com.popq.platform.qna.entity.QnaStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// 요청한 데이터를 받아 뿌릴 답변 규격

@Getter
@Builder
public class QnaResponse {

    private Long qnaId;
    private QnaCategory category;
    private Long writerId;
    private String writerName;
    private String title;
    private String content;
    private boolean secret;
    private boolean accessible;
    private QnaStatus status;

    private String answerContent;
    private String responderName;
    private LocalDateTime answeredAt;
    private LocalDateTime createdAt;

    public static QnaResponse from(Qna qna, boolean canViewSecret) {
        boolean isBlocked = qna.isSecret() && !canViewSecret;

        return QnaResponse.builder()
                .qnaId(qna.getQnaId())
                .category(qna.getCategory())
                .writerId(qna.getWriter().getId())
                .writerName(maskName(qna.getWriter().getName()))
                .title(isBlocked ? "비밀글입니다." : qna.getTitle())
                .content(isBlocked ? "작성자와 관리자만 열람할 수 있는 비밀글입니다." : qna.getContent())
                .secret(qna.isSecret())
                .accessible(!isBlocked)
                .status(qna.getStatus())
                .answerContent(isBlocked ? null : qna.getAnswerContent())
                .responderName(qna.getAnsweredBy() != null ? qna.getAnsweredBy().getName() : null)
                .answeredAt(isBlocked ? null : qna.getAnsweredAt())
                .createdAt(qna.getCreatedAt())
                .build();
    }

    private static String maskName(String name) { // 마스킹
        if (name == null || name.length() < 2) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}