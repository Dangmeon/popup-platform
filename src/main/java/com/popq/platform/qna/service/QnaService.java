package com.popq.platform.qna.service;

import com.popq.platform.entity.Role;
import com.popq.platform.entity.User;
import com.popq.platform.qna.dto.QnaAnswerRequest;
import com.popq.platform.qna.dto.QnaCreateRequest;
import com.popq.platform.qna.dto.QnaResponse;
import com.popq.platform.qna.entity.Qna;
import com.popq.platform.qna.entity.QnaCategory;
import com.popq.platform.qna.entity.QnaStatus;
import com.popq.platform.qna.repository.QnaRepository;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QnaService {

    private final QnaRepository qnaRepository;
    private final UserRepository userRepository;

    // 로그인 회원 문의 등록
    @Transactional
    public QnaResponse createQna(String email, QnaCreateRequest request) {
        User writer = getUserByEmail(email);

        Qna qna = Qna.builder()
                .writer(writer)
                .category(request.getCategory())
                .title(request.getTitle())
                .content(request.getContent())
                .isSecret(request.isSecret())
                .build();

        return QnaResponse.from(qnaRepository.save(qna), true); // 작성자 본인은 볼 수 있게
    }

    // QnA 목록 조회 비로그인 포함 누구나 가능, 비밀글은 마스킹처리
    @Transactional(readOnly = true)
    public Page<QnaResponse> getQnaList(QnaCategory category, QnaStatus status, String email, Pageable pageable) {
        User currentUser = (email != null) ? userRepository.findByUsername(email).orElse(null) : null;

        return qnaRepository.findQnasWithFilters(category, status, pageable)
                .map(qna -> QnaResponse.from(qna, canViewSecret(qna, currentUser)));
    }

    // QnA 단건 상세 조회 (비밀글 권한 없으면 403 띄우기)
    @Transactional(readOnly = true)
    public QnaResponse getQnaDetail(Long qnaId, String email) {
        Qna qna = getQna(qnaId);
        User currentUser = (email != null) ? userRepository.findByUsername(email).orElse(null) : null;

        if (qna.isSecret() && !canViewSecret(qna, currentUser)) {
            throw new AccessDeniedException("비밀글은 작성자와 관리자만 열람할 수 있습니다.");
        }

        return QnaResponse.from(qna, true);
    }

    // 문의 수정(카테고리, 제목, 내용, 비밀글 여부) 본인이 작성한 것만 가능(나머진 403)
    @Transactional
    public QnaResponse updateQna(Long qnaId, String email, QnaCreateRequest request) {
        User currentUser = getUserByEmail(email);
        Qna qna = getQna(qnaId);

        if (!qna.getWriter().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("본인이 작성한 문의만 수정할 수 있습니다.");
        }

        qna.updateQuestion(request.getCategory(), request.getTitle(), request.getContent(), request.isSecret());
        return QnaResponse.from(qna, true);
    }

    // 문의 삭제 (작성자 본인 또는 ADMIN만 가능)
    @Transactional
    public void deleteQna(Long qnaId, String email) {
        User currentUser = getUserByEmail(email);
        Qna qna = getQna(qnaId);

        boolean isWriter = qna.getWriter().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN;

        if (!isWriter && !isAdmin) {
            throw new AccessDeniedException("문의를 삭제할 권한이 없습니다.");
        }

        qnaRepository.delete(qna);
    }

    // 6. [ADMIN] 답변 등록 및 수정
    @Transactional
    public QnaResponse answerQna(Long qnaId, String email, QnaAnswerRequest request) {
        User responder = getUserByEmail(email);
        if (responder.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("고객센터 답변은 최고 관리자(ADMIN)만 등록할 수 있습니다.");
        }

        Qna qna = getQna(qnaId);
        qna.registerAnswer(request.getAnswerContent(), responder);
        return QnaResponse.from(qna, true);
    }

    // --- 헬퍼 메서드 ---

    private boolean canViewSecret(Qna qna, User user) {
        if (user == null) return false;
        if (user.getRole() == Role.ADMIN) return true;
        return qna.getWriter().getId().equals(user.getId());
    }

    private User getUserByEmail(String email) {
        if (email == null) {
            throw new AccessDeniedException("로그인이 필요한 서비스입니다.");
        }
        return userRepository.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("회원 정보를 찾을 수 없습니다."));
    }

    private Qna getQna(Long qnaId) {
        return qnaRepository.findById(qnaId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 문의글입니다. ID: " + qnaId));
    }
}
