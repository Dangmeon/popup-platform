package com.popq.platform.service;

import com.popq.platform.entity.Role; // Role Enum 임포트
import com.popq.platform.entity.User;
import com.popq.platform.dto.AdminRoleUpdateResponse;
import com.popq.platform.dto.MemberInfoResponse;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;

    /**
     * 1. 전체 회원 목록 페이징 조회
     */
    @Transactional(readOnly = true)
    public Page<MemberInfoResponse> getAllMembers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(MemberInfoResponse::from);
    }

    /**
     * 2. 특정 회원 권한 수정
     */
    @Transactional
    public AdminRoleUpdateResponse updateMemberRole(Long memberId, String newRoleStr) {
        // User 엔티티 조회
        User user = userRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원을 찾을 수 없습니다. ID: " + memberId));

        // String으로 넘어온 권한 값을 Role Enum으로 변환 후 엔티티 메서드 호출
        Role newRole = Role.valueOf(newRoleStr);
        user.updateRole(newRole);

        // 응답 DTO 반환 (Role Enum을 String으로 변환하여 전달하려면 .name() 사용)
        return new AdminRoleUpdateResponse(user.getId(), user.getRole().name());
    }
}