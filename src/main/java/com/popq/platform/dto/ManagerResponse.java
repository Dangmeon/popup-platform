package com.popq.platform.dto;

import com.popq.platform.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 매니저 목록 응답 (배정 드롭다운용) : 비밀번호, 전화번호는 내보내지 않음
@Getter
@AllArgsConstructor
@Builder
public class ManagerResponse {
    private Long id;       // 회원 번호 (member_id)
    private String name;
    private String email;  // 배정 API에 보낼 값

    public static ManagerResponse toDto(User user) {
        return ManagerResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getUsername())
                .build();
    }
}
