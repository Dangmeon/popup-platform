package com.popq.platform.dto;

import com.popq.platform.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class MemberInfoResponse {
    private Long memberId;
    private String email;
    private String name;
    private String phone;
    private String role;
    private String createdAt;

    // Entity를 DTO로 변환하는 정적 메서드
    public static MemberInfoResponse from(User user) {
        String formattedCreatedAt = "";
        if (user.getCreatedAt() != null) {
            formattedCreatedAt = user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }

        return MemberInfoResponse.builder()
                .memberId(user.getId())
                .email(user.getUsername())
                .name(user.getName())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().name() : "USER")
                .createdAt(formattedCreatedAt)
                .build();
    }
}