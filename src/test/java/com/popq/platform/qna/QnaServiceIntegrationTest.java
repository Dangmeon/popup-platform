package com.popq.platform.qna;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.qna.dto.QnaAnswerRequest;
import com.popq.platform.qna.dto.QnaCreateRequest;
import com.popq.platform.qna.dto.QnaResponse;
import com.popq.platform.qna.entity.Qna;
import com.popq.platform.qna.entity.QnaCategory;
import com.popq.platform.qna.entity.QnaStatus;
import com.popq.platform.qna.exception.QnaErrorCode;
import com.popq.platform.qna.exception.QnaException;
import com.popq.platform.qna.repository.QnaRepository;
import com.popq.platform.qna.service.QnaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class QnaServiceIntegrationTest {

    @Autowired
    private QnaService qnaService;

    @Autowired
    private QnaRepository qnaRepository;

    @Autowired
    private UserRepository userRepository;

    private User writerA;
    private User otherUserB;
    private User managerUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        writerA = userRepository.save(User.builder()
                .username("junit_qna_writera@test.com")
                .password("encodedPw")
                .name("문의작성자A")
                .phone("010-9797-0001")
                .role(Role.USER)
                .build());

        otherUserB = userRepository.save(User.builder()
                .username("junit_qna_userb@test.com")
                .password("encodedPw")
                .name("타인유저B")
                .phone("010-9797-0002")
                .role(Role.USER)
                .build());

        managerUser = userRepository.save(User.builder()
                .username("junit_qna_manager@test.com")
                .password("encodedPw")
                .name("일반매니저")
                .phone("010-9797-0003")
                .role(Role.MANAGER)
                .build());

        adminUser = userRepository.save(User.builder()
                .username("junit_qna_admin@test.com")
                .password("encodedPw")
                .name("총괄관리자")
                .phone("010-9797-0004")
                .role(Role.ADMIN)
                .build());
    }

    @AfterEach
    void tearDown() {
        List<Long> testUserIds = List.of(
                writerA.getId(), otherUserB.getId(), managerUser.getId(), adminUser.getId()
        );

        List<Qna> testQnas = qnaRepository.findAll().stream()
                .filter(q -> testUserIds.contains(q.getWriter().getId()))
                .toList();
        qnaRepository.deleteAllInBatch(testQnas);

        userRepository.deleteAllInBatch(List.of(writerA, otherUserB, managerUser, adminUser));
    }

    @Test
    @DisplayName("통합 1: QnA 등록 -> 단건 조회 -> ADMIN 답변 등록 시 상태가 WAITING에서 ANSWERED로 전이된다")
    void qnaFullLifecycle_CreateAndAdminAnswer_Success() {
        // 1. 문의 등록
        QnaCreateRequest createReq = createQnaRequest(
                QnaCategory.RESERVATION,
                "[JUnit] 현장 대기 취소 후 재등록 문의",
                "취소 후 바로 재등록 가능한가요?",
                false
        );
        QnaResponse created = qnaService.createQna(writerA.getUsername(), createReq);

        assertThat(created.getQnaId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(QnaStatus.WAITING);
        assertThat(created.isAccessible()).isTrue();

        // 2. ADMIN 답변 등록
        QnaAnswerRequest answerReq = createAnswerRequest("안녕하세요, 고객센터입니다. 즉시 재등록 가능합니다.");
        QnaResponse answered = qnaService.answerQna(created.getQnaId(), adminUser.getUsername(), answerReq);

        assertThat(answered.getStatus()).isEqualTo(QnaStatus.ANSWERED);
        assertThat(answered.getAnswerContent()).isEqualTo("안녕하세요, 고객센터입니다. 즉시 재등록 가능합니다.");
        assertThat(answered.getResponderName()).isEqualTo(adminUser.getName());
        assertThat(answered.getAnsweredAt()).isNotNull();

        // 3. DB 상태 재검증
        Qna saved = qnaRepository.findById(created.getQnaId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(QnaStatus.ANSWERED);
        assertThat(saved.getAnsweredBy().getId()).isEqualTo(adminUser.getId());
    }

    @Test
    @DisplayName("통합 2: 작성자 본인은 미답변(WAITING) 문의를 수정 및 삭제할 수 있다")
    void updateAndDeleteOwnWaitingQna_Success() {
        QnaResponse created = qnaService.createQna(
                writerA.getUsername(),
                createQnaRequest(QnaCategory.ETC, "[JUnit] 원본 제목", "원본 내용", false)
        );

        // 수정
        QnaResponse updated = qnaService.updateQna(
                created.getQnaId(),
                writerA.getUsername(),
                createQnaRequest(QnaCategory.ACCOUNT, "[JUnit] 수정된 제목", "수정된 내용", true)
        );

        assertThat(updated.getTitle()).isEqualTo("[JUnit] 수정된 제목");
        assertThat(updated.getContent()).isEqualTo("수정된 내용");
        assertThat(updated.getCategory()).isEqualTo(QnaCategory.ACCOUNT);
        assertThat(updated.isSecret()).isTrue();

        // 삭제
        qnaService.deleteQna(created.getQnaId(), writerA.getUsername());
        assertThat(qnaRepository.findById(created.getQnaId())).isEmpty();
    }

    @Test
    @DisplayName("통합 3: 답변 완료(ANSWERED)된 문의는 작성자 본인이라도 수정할 수 없다")
    void updateQna_Fail_WhenAlreadyAnswered() {
        QnaResponse created = qnaService.createQna(
                writerA.getUsername(),
                createQnaRequest(QnaCategory.ETC, "[JUnit] 답변 완료 수정 불가 테스트", "내용", false)
        );
        qnaService.answerQna(created.getQnaId(), adminUser.getUsername(), createAnswerRequest("답변 완료"));

        QnaCreateRequest updateReq = createQnaRequest(QnaCategory.ETC, "수정 시도", "수정 내용", false);

        assertThatThrownBy(() -> qnaService.updateQna(created.getQnaId(), writerA.getUsername(), updateReq))
                .isInstanceOf(QnaException.class)
                .extracting("errorCode")
                .isEqualTo(QnaErrorCode.ANSWERED_QNA_CANNOT_BE_MODIFIED);
    }

    @Test
    @DisplayName("권한 4: 비밀글은 작성자와 ADMIN만 상세 조회할 수 있고, 비로그인 및 타 사용자는 차단된다")
    void secretQnaDetail_AccessControl() {
        QnaResponse secretQna = qnaService.createQna(
                writerA.getUsername(),
                createQnaRequest(QnaCategory.ACCOUNT, "[JUnit] 비밀 계좌 문의", "110-123-456789 환불 요청", true)
        );

        // 비로그인 차단
        assertThatThrownBy(() -> qnaService.getQnaDetail(secretQna.getQnaId(), null))
                .isInstanceOf(QnaException.class)
                .extracting("errorCode")
                .isEqualTo(QnaErrorCode.FORBIDDEN_SECRET_QNA);

        // 타 사용자 차단
        assertThatThrownBy(() -> qnaService.getQnaDetail(secretQna.getQnaId(), otherUserB.getUsername()))
                .isInstanceOf(QnaException.class)
                .extracting("errorCode")
                .isEqualTo(QnaErrorCode.FORBIDDEN_SECRET_QNA);

        // 작성자 본인 허용
        QnaResponse byWriter = qnaService.getQnaDetail(secretQna.getQnaId(), writerA.getUsername());
        assertThat(byWriter.isAccessible()).isTrue();
        assertThat(byWriter.getContent()).isEqualTo("110-123-456789 환불 요청");

        // ADMIN 허용
        QnaResponse byAdmin = qnaService.getQnaDetail(secretQna.getQnaId(), adminUser.getUsername());
        assertThat(byAdmin.isAccessible()).isTrue();
        assertThat(byAdmin.getContent()).isEqualTo("110-123-456789 환불 요청");
    }

    @Test
    @DisplayName("권한 5: 목록 조회 시 비밀글은 비로그인/타인에게 마스킹(accessible=false)되고 작성자/ADMIN에게는 원문이 노출된다")
    void qnaList_SecretMaskingByViewer() {
        QnaResponse secretQna = qnaService.createQna(
                writerA.getUsername(),
                createQnaRequest(QnaCategory.ACCOUNT, "[JUnit] 목록 마스킹 비밀글", "민감한 본문 내용", true)
        );

        // 타인 목록 조회 -> 마스킹 확인
        Page<QnaResponse> listForOther = qnaService.getQnaList(
                QnaCategory.ACCOUNT, QnaStatus.WAITING, otherUserB.getUsername(), PageRequest.of(0, 50)
        );
        QnaResponse maskedItem = listForOther.getContent().stream()
                .filter(r -> r.getQnaId().equals(secretQna.getQnaId()))
                .findFirst()
                .orElseThrow();
        assertThat(maskedItem.isAccessible()).isFalse();
        assertThat(maskedItem.getContent()).isNotEqualTo("민감한 본문 내용");

        // 작성자 본인 목록 조회 -> 원문 노출 확인
        Page<QnaResponse> listForWriter = qnaService.getQnaList(
                QnaCategory.ACCOUNT, QnaStatus.WAITING, writerA.getUsername(), PageRequest.of(0, 50)
        );
        QnaResponse visibleItem = listForWriter.getContent().stream()
                .filter(r -> r.getQnaId().equals(secretQna.getQnaId()))
                .findFirst()
                .orElseThrow();
        assertThat(visibleItem.isAccessible()).isTrue();
        assertThat(visibleItem.getContent()).isEqualTo("민감한 본문 내용");
    }

    @Test
    @DisplayName("권한 6: 타인 또는 ADMIN은 남의 문의를 수정할 수 없고, 삭제는 작성자 본인 또는 ADMIN만 가능하다")
    void modifyAndDelete_IdorAndAdminPolicy() {
        QnaResponse created = qnaService.createQna(
                writerA.getUsername(),
                createQnaRequest(QnaCategory.ETC, "[JUnit] 권한 검증 문의", "내용", false)
        );
        QnaCreateRequest updateReq = createQnaRequest(QnaCategory.ETC, "해킹 수정", "변조", false);

        // 타 사용자 수정 차단
        assertThatThrownBy(() -> qnaService.updateQna(created.getQnaId(), otherUserB.getUsername(), updateReq))
                .isInstanceOf(QnaException.class)
                .extracting("errorCode")
                .isEqualTo(QnaErrorCode.FORBIDDEN_QNA_MODIFICATION);

        // ADMIN이라도 타인 문의 수정은 불가
        assertThatThrownBy(() -> qnaService.updateQna(created.getQnaId(), adminUser.getUsername(), updateReq))
                .isInstanceOf(QnaException.class)
                .extracting("errorCode")
                .isEqualTo(QnaErrorCode.FORBIDDEN_QNA_MODIFICATION);

        // 타 사용자 삭제 차단
        assertThatThrownBy(() -> qnaService.deleteQna(created.getQnaId(), otherUserB.getUsername()))
                .isInstanceOf(QnaException.class)
                .extracting("errorCode")
                .isEqualTo(QnaErrorCode.FORBIDDEN_QNA_DELETION);

        // ADMIN 삭제는 허용
        qnaService.deleteQna(created.getQnaId(), adminUser.getUsername());
        assertThat(qnaRepository.findById(created.getQnaId())).isEmpty();
    }

    @Test
    @DisplayName("권한 7: 일반 회원(USER)과 매니저(MANAGER)는 QnA 답변을 등록할 수 없다 (ADMIN 전용)")
    void answerQna_Fail_WhenNotAdmin() {
        QnaResponse created = qnaService.createQna(
                writerA.getUsername(),
                createQnaRequest(QnaCategory.ETC, "[JUnit] 관리자 답변 권한 테스트", "내용", false)
        );
        QnaAnswerRequest answerReq = createAnswerRequest("무단 답변 시도");

        // 일반 유저 차단
        assertThatThrownBy(() -> qnaService.answerQna(created.getQnaId(), otherUserB.getUsername(), answerReq))
                .isInstanceOf(QnaException.class)
                .extracting("errorCode")
                .isEqualTo(QnaErrorCode.FORBIDDEN_ADMIN_ONLY);

        // 팝업 매니저 차단
        assertThatThrownBy(() -> qnaService.answerQna(created.getQnaId(), managerUser.getUsername(), answerReq))
                .isInstanceOf(QnaException.class)
                .extracting("errorCode")
                .isEqualTo(QnaErrorCode.FORBIDDEN_ADMIN_ONLY);
    }

    // --- DTO 인스턴스 생성 헬퍼 ---

    private QnaCreateRequest createQnaRequest(QnaCategory category, String title, String content, boolean isSecret) {
        try {
            Constructor<QnaCreateRequest> ctor = QnaCreateRequest.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            QnaCreateRequest req = ctor.newInstance();
            ReflectionTestUtils.setField(req, "category", category);
            ReflectionTestUtils.setField(req, "title", title);
            ReflectionTestUtils.setField(req, "content", content);

            // 필드명이 secret인지 isSecret인지 자동 판별
            boolean hasIsSecret = java.util.Arrays.stream(QnaCreateRequest.class.getDeclaredFields())
                    .anyMatch(f -> f.getName().equals("isSecret"));
            ReflectionTestUtils.setField(req, hasIsSecret ? "isSecret" : "secret", isSecret);
            return req;
        } catch (Exception e) {
            throw new RuntimeException("QnaCreateRequest 생성 실패", e);
        }
    }
    private QnaAnswerRequest createAnswerRequest(String answerContent) {
        try {
            Constructor<QnaAnswerRequest> ctor = QnaAnswerRequest.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            QnaAnswerRequest req = ctor.newInstance();
            ReflectionTestUtils.setField(req, "answerContent", answerContent);
            return req;
        } catch (Exception e) {
            throw new RuntimeException("QnaAnswerRequest 생성 실패", e);
        }
    }
}