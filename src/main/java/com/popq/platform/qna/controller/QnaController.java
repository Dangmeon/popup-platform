package com.popq.platform.qna.controller;

import com.popq.platform.qna.dto.QnaAnswerRequest;
import com.popq.platform.qna.dto.QnaCreateRequest;
import com.popq.platform.qna.dto.QnaResponse;
import com.popq.platform.qna.entity.QnaCategory;
import com.popq.platform.qna.entity.QnaStatus;
import com.popq.platform.qna.service.QnaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 각 메서드에 맞게 API 매핑

@RestController
@RequiredArgsConstructor
public class QnaController {

    private final QnaService qnaService;

    // 고객센터 QnA 목록 조회 API (비로그인도 열람 자체는 허용, 카테고리/상태 필터)
    @GetMapping("/api/qnas")
    public ResponseEntity<Page<QnaResponse>> getQnaList(
            @RequestParam(required = false) QnaCategory category,
            @RequestParam(required = false) QnaStatus status,
            @AuthenticationPrincipal String email,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(qnaService.getQnaList(category, status, email, pageable));
    }

    // 문의 등록 (로그인 회원)
    @PostMapping("/api/qnas")
    public ResponseEntity<QnaResponse> createQna(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody QnaCreateRequest request) {
        return ResponseEntity.ok(qnaService.createQna(email, request));
    }

    // QnA 단건 상세 조회
    @GetMapping("/api/qnas/{qnaId}")
    public ResponseEntity<QnaResponse> getQnaDetail(
            @PathVariable Long qnaId,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(qnaService.getQnaDetail(qnaId, email));
    }

    // 문의 수정 (작성자 본인)
    @PutMapping("/api/qnas/{qnaId}")
    public ResponseEntity<QnaResponse> updateQna(
            @PathVariable Long qnaId,
            @AuthenticationPrincipal String email,
            @Valid @RequestBody QnaCreateRequest request) {
        return ResponseEntity.ok(qnaService.updateQna(qnaId, email, request));
    }

    // 문의 삭제 (작성자 본인 or ADMIN)
    @DeleteMapping("/api/qnas/{qnaId}")
    public ResponseEntity<Void> deleteQna(
            @PathVariable Long qnaId,
            @AuthenticationPrincipal String email) {
        qnaService.deleteQna(qnaId, email);
        return ResponseEntity.noContent().build();
    }

    // [관리자] 답변 등록 및 수정
    @PostMapping("/api/admin/qnas/{qnaId}/answer")
    public ResponseEntity<QnaResponse> answerQna(
            @PathVariable Long qnaId,
            @AuthenticationPrincipal String email,
            @Valid @RequestBody QnaAnswerRequest request) {
        return ResponseEntity.ok(qnaService.answerQna(qnaId, email, request));
    }
}