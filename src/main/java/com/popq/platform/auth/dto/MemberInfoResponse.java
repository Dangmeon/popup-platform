package com.popq.platform.auth.dto;

import com.popq.platform.auth.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;

@Getter
@Builder
public class MemberInfoResponse {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Long memberId;
    private String email;
    private String name;
    private String phone;
    private String role;
    private String createdAt;

    public static MemberInfoResponse from(User user) {
        String formattedCreatedAt = user.getCreatedAt() != null
                ? user.getCreatedAt().format(FORMATTER)
                : "";

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