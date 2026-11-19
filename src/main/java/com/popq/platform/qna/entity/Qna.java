package com.popq.platform.qna.entity;

import com.popq.platform.entity.User;
import com.popq.platform.qna.exception.QnaErrorCode;
import com.popq.platform.qna.exception.QnaException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// DB 매핑 및 Getter, 여러 메서드

@Entity
@Table(name = "qna")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Qna {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "qna_seq_gen")
    @SequenceGenerator(name = "qna_seq_gen", sequenceName = "qna_seq", allocationSize = 1)
    @Column(name = "qna_id")
    private Long qnaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private User writer;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private QnaCategory category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @Column(name = "is_secret", nullable = false)
    private boolean isSecret;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QnaStatus status;

    // --- 1:1 답변 영역 ---
    @Column(name = "answer_content", length = 2000)
    private String answerContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answered_by")
    private User answeredBy;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

//    @PrePersist
//    public void prePersist() {
//        LocalDateTime now = LocalDateTime.now();
//        this.createdAt = now;
//        this.updatedAt = now;
//    }
//
//    @PreUpdate
//    public void preUpdate() {
//        this.updatedAt = LocalDateTime.now();
//    }

    @Builder
    public Qna(User writer, QnaCategory category, String title, String content, boolean isSecret) {
        this.writer = writer;
        this.category = (category != null) ? category : QnaCategory.ETC;
        this.title = title;
        this.content = content;
        this.isSecret = isSecret;
        this.status = QnaStatus.WAITING;
    }

    public void updateQuestion(QnaCategory category, String title, String content, boolean isSecret) {
        if (this.status == QnaStatus.ANSWERED) {
            throw new QnaException(QnaErrorCode.ANSWERED_QNA_CANNOT_BE_MODIFIED);
        }
        if (category != null) {
            this.category = category;
        }
        this.title = title;
        this.content = content;
        this.isSecret = isSecret;
    }

    public void registerAnswer(String answerContent, User responder) {
        this.answerContent = answerContent;
        this.answeredBy = responder;
        this.answeredAt = LocalDateTime.now();
        this.status = QnaStatus.ANSWERED;
    }
}