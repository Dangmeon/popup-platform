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

    /** 테스트 결과 GET http://localhost:8080/api/qnas
     {
     "content": [
         {
             "accessible": false,
             "answerContent": null,
             "answeredAt": null,
             "category": "ACCOUNT",
             "content": "작성자와 관리자만 열람할 수 있는 비밀글입니다.",
             "createdAt": "2026-11-18T14:08:52.450382",
             "qnaId": 3,
             "responderName": null,
             "secret": true,
             "status": "WAITING",
             "title": "비밀글입니다.",
             "writerId": 12,
             "writerName": "이*희"
         },
         {
             "accessible": true,
             "answerContent": null,
             "answeredAt": null,
             "category": "RESERVATION",
             "content": "실수로 취소했는데 바로 다시 줄 서도 되는지 궁금합니다.",
             "createdAt": "2026-11-18T11:52:44.100692",
             "qnaId": 2,
             "responderName": null,
             "secret": false,
             "status": "WAITING",
             "title": "현장 대기 취소 후 재등록 가능한가요?",
             "writerId": 11,
             "writerName": "김*수"
         },
         {
             "accessible": true,
             "answerContent": null,
             "answeredAt": null,
             "category": "RESERVATION",
             "content": "실수로 취소했는데 바로 다시 줄 서도 되는지 궁금합니다.",
             "createdAt": "2026-11-18T11:37:20.467813",
             "qnaId": 1,
             "responderName": null,
             "secret": false,
             "status": "WAITING",
             "title": "현장 대기 취소 후 재등록 가능한가요?",
             "writerId": 11,
             "writerName": "김*수"
         }
     ],
             "empty": false,
             "first": true,
             "last": true,
             "number": 0,
             "numberOfElements": 3,
             "pageable": {
             "offset": 0,
             "pageNumber": 0,
             "pageSize": 10,
             "paged": true,
             "sort": {
             "empty": true,
             "sorted": false,
             "unsorted": true
         },
             "unpaged": false
         },
         "size": 10,
         "sort": {
             "empty": true,
             "sorted": false,
             "unsorted": true
         },
         "totalElements": 3,
         "totalPages": 1
     }
     */

    // 문의 등록 (로그인 회원)
    @PostMapping("/api/qnas")
    public ResponseEntity<QnaResponse> createQna(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody QnaCreateRequest request) {
        return ResponseEntity.ok(qnaService.createQna(email, request));
    }

    /**
     *  테스트 결과 POST http://localhost:8080/api/qnas
         {
             "accessible": true,
             "answerContent": null,
             "answeredAt": null,
             "category": "RESERVATION",
             "content": "실수로 취소했는데 바로 다시 줄 서도 되는지 궁금합니다.",
             "createdAt": null,
             "qnaId": 1,
             "responderName": null,
             "secret": false,
             "status": "WAITING",
             "title": "현장 대기 취소 후 재등록 가능한가요?",
             "writerId": 11,
             "writerName": "김*수"
         }

        비밀글 등록
         {
             "accessible": true,
             "answerContent": null,
             "answeredAt": null,
             "category": "ACCOUNT",
             "content": "개인 계좌번호 110-123-456789로 환불 처리가 가능한가요?",
             "createdAt": null,
             "qnaId": 3,
             "responderName": null,
             "secret": true,
             "status": "WAITING",
             "title": "결제 계좌 및 환불 문의드립니다.",
             "writerId": 12,
             "writerName": "이*희"
         }
     */

    // QnA 단건 상세 조회
    @GetMapping("/api/qnas/{qnaId}")
    public ResponseEntity<QnaResponse> getQnaDetail(
            @PathVariable Long qnaId,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(qnaService.getQnaDetail(qnaId, email));
    }

    /** 테스트 결과 GET http://localhost:8080/api/qnas/3
     *  비밀글을 인가되지 않은 사용자 접근 시 예외 뱉는거 확인.. 추후 커스텀 예외 처리 예정
     */

    // 문의 수정 (작성자 본인)
    @PutMapping("/api/qnas/{qnaId}")
    public ResponseEntity<QnaResponse> updateQna(
            @PathVariable Long qnaId,
            @AuthenticationPrincipal String email,
            @Valid @RequestBody QnaCreateRequest request) {
        return ResponseEntity.ok(qnaService.updateQna(qnaId, email, request));
    }

    /** 테스트 완료 PUT http://localhost:8080/api/qnas/2
     * 인가되지 않은 이용자 수정 불가 예외 뱉는거 확인.. 추후 커스텀 예외 처리 예정
     * 작성자 본인만 수정 가능 관리자도 불가
     * 답변 완료 후 본인 수정 시도 시 "답변이 완료된 문의는 수정할 수 없습니다." 던지기
     {
         "accessible": true,
         "answerContent": null,
         "answeredAt": null,
         "category": "POPUP",
         "content": "현장 대기 취소 후 10분 뒤에 다시 웨이팅 등록이 가능한지 확인 부탁드립니다.",
         "createdAt": "2026-11-18T11:52:44.100692",
         "qnaId": 2,
         "responderName": null,
         "secret": true,
         "status": "WAITING",
         "title": "[수정됨] 팝업스토어 입장 대기 취소 후 재등록 문의",
         "writerId": 11,
         "writerName": "김*수"
     }
     */

    // 문의 삭제 (작성자 본인)
    @DeleteMapping("/api/qnas/{qnaId}")
    public ResponseEntity<Void> deleteQna(
            @PathVariable Long qnaId,
            @AuthenticationPrincipal String email) {
        qnaService.deleteQna(qnaId, email);
        return ResponseEntity.noContent().build();
    }

    /** 테스트 완료 DELETE http://localhost:8080/api/qnas/~
     * 타 사용자가 문의 삭제 시도 시 "문의를 삭제할 권한이 없습니다." 예외 던진거 확인
     * 관리자 & 작성자 본인 삭제 시도시 204 No Content 뜨는거 확인
     */

    // [관리자] 답변 등록 및 수정
    @PostMapping("/api/admin/qnas/{qnaId}/answer")
    public ResponseEntity<QnaResponse> answerQna(
            @PathVariable Long qnaId,
            @AuthenticationPrincipal String email,
            @Valid @RequestBody QnaAnswerRequest request) {
        return ResponseEntity.ok(qnaService.answerQna(qnaId, email, request));
    }

    /** 테스트 결과 POST http://localhost:8080/api/admin/qnas/{qnaId}/answer
     *  일반 회원 답변 등록 시도 시 고객센터 답변은 최고 관리자(ADMIN)만 등록할 수 있습니다. 예외 반환,, 추후 전역 예외 처리 예정
     *  {
     *     "accessible": true,
     *     "answerContent": "안녕하세요, 고객센터입니다. 3일 내로 환불이 진행될 예정입니다. 감사합니다.",
     *     "answeredAt": "2026-11-18T15:32:00.2956552",
     *     "category": "ACCOUNT",
     *     "content": "개인 계좌번호 110-123-456789로 환불 처리가 가능한가요?",
     *     "createdAt": "2026-11-18T14:08:52.450382",
     *     "qnaId": 3,
     *     "responderName": "관리자",
     *     "secret": true,
     *     "status": "ANSWERED",
     *     "title": "결제 계좌 및 환불 문의드립니다.",
     *     "writerId": 12,
     *     "writerName": "이*희"
     * }
     */
}