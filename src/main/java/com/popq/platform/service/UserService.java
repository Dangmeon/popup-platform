package com.popq.platform.service;

import com.popq.platform.config.JwtTokenProvider;
import com.popq.platform.dto.LoginRequest;
import com.popq.platform.dto.SignupRequest;
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
    private final JwtTokenProvider jwtTokenProvider; // 토큰 생성을 위해 추가

    @Transactional
    public void signup(SignupRequest requestDto) {
        String username = requestDto.getUsername();

        // 아이디 중복 확인
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        // 빌더 패턴을 이용한 안전한 사용자 엔티티 생성 및 저장
        User user = User.builder()
                .username(username)
                .password(encodedPassword)
                .name(requestDto.getName())
                .phone(requestDto.getPhone())
                .role(Role.USER) // 기본 회원가입 시 USER 권한 부여
                .build();

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public String login(LoginRequest requestDto) {
        // 1. 회원 존재 여부 확인
        User user = userRepository.findByUsername(requestDto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일(아이디)입니다."));

        // 2. 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 3. JWT 토큰 생성 및 반환
        return jwtTokenProvider.createToken(user.getUsername(), user.getRole().name());
    }
}