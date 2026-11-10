package com.popq.platform.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "member_seq_generator")
    @SequenceGenerator(name = "member_seq_generator", sequenceName = "member_seq", allocationSize = 1)
    @Column(name = "member_id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String username; //로그인 ID(이메일) 역할

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    // --- Soft Delete ---
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE; // 기본값 활성 상태

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt; // 탈퇴 일시

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public User(String username, String password, String name, String phone, Role role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.phone = phone;
        this.role = (role != null) ? role : Role.USER;
        this.status = UserStatus.ACTIVE; // 생성 시 기본 활성
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void updateMemberInfo(String name, String phone) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (phone != null && !phone.isBlank()) {
            this.phone = phone;
        }
    }

    // 회원탈퇴 상태 변경 메서드
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.withdrawnAt = LocalDateTime.now();
    }

    // 회원 상태를 정의
    public enum UserStatus {
        ACTIVE,   // 정상 활동 회원
        WITHDRAWN // 탈퇴 유예 중인 회원
    }
}