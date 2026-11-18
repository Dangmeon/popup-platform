package com.popq.platform.service;

import com.popq.platform.config.JwtTokenProvider;
import com.popq.platform.dto.*;
import com.popq.platform.entity.Role;
import com.popq.platform.entity.User;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public void signup(SignupRequest requestDto) {
        String username = requestDto.getUsername();

        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        User user = User.builder()
                .username(username)
                .password(encodedPassword)
                .name(requestDto.getName())
                .phone(requestDto.getPhone())
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public String login(LoginRequest requestDto) {
        User user = userRepository.findByUsername(requestDto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일(아이디)입니다."));

        // 탈퇴한 회원인지 확인
        if (user.getStatus() == User.UserStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴한 회원입니다.");
        }

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // role이 null일 경우 방어 처리 (기본값 USER)
        String roleName = (user.getRole() != null) ? user.getRole().name() : Role.USER.name();

        return jwtTokenProvider.createToken(user.getUsername(), roleName);
    }

    // 회원 정보 조회 로직
    @Transactional(readOnly = true)
    public MemberInfoResponse getMyInfo(String email) {
        User user = userRepository.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        return MemberInfoResponse.from(user);
    }

    // 회원 정보 및 비밀번호 수정 로직
    @Transactional
    public MemberInfoResponse updateMyInfo(String email, MemberUpdateRequest requestDto) {
        User user = userRepository.findByUsername(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 이름 및 전화번호 수정
        user.updateMemberInfo(requestDto.getName(), requestDto.getPhone());

        // 비밀번호 수정 요청이 있는 경우 암호화 후 반영
        if (requestDto.getPassword() != null && !requestDto.getPassword().isBlank()) {
            String encodedPassword = passwordEncoder.encode(requestDto.getPassword());
            user.updatePassword(encodedPassword);
        }

        // 수정된 회원 정보를 담아 응답 DTO 반환
        return MemberInfoResponse.from(user);
    }

    // 회원탈퇴 로직 (Soft Delete)
    @Transactional
    public void withdraw(String username) {
        // 회원 존재 여부 확인
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        // 이미 탈퇴된 회원인지 방어 검증
        if (user.getStatus() == User.UserStatus.WITHDRAWN) {
            throw new IllegalArgumentException("이미 탈퇴 처리된 회원입니다.");
        }

        user.withdraw();
    }
}