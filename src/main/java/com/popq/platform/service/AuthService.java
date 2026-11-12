package com.popq.platform.service;

import com.popq.platform.dto.LoginRequest;
import com.popq.platform.dto.PasswordFindRequest;
import com.popq.platform.dto.SignupRequest;
import com.popq.platform.entity.Role;
import com.popq.platform.entity.User;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender;

    @Transactional
    public void signup(SignupRequest requestDto) {
        // 이메일(아이디) 중복 검사
        if (userRepository.existsByUsername(requestDto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        // 역할 설정 (기본값: USER, 요청값이 "MANAGER"면 MANAGER 부여)
        Role userRole = Role.USER;
        if (requestDto.getRole() != null && requestDto.getRole().trim().toUpperCase().equals("MANAGER")) {
            userRole = Role.MANAGER;
        }

        // 유저 엔티티 생성 및 저장
        User user = User.builder()
                .username(requestDto.getUsername())
                .password(encodedPassword)
                .name(requestDto.getName())
                .phone(requestDto.getPhone())
                .role(userRole)
                .build();

        userRepository.save(user);
    }

    @Transactional
    public String login(LoginRequest requestDto) {
        // 유저 확인
        User user = userRepository.findByUsername(requestDto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));

        // 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 로그인 성공 시 토큰 반환
        return "로그인 성공 토큰 (임시)";
    }

    @Transactional
    public void findPassword(PasswordFindRequest requestDto) {
        // 1. 가입된 사용자 확인
        User user = userRepository.findByUsername(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // 2. 8자리 랜덤 임시 비밀번호 생성
        String tempPassword = UUID.randomUUID().toString().substring(0, 8);

        // 3. 비밀번호 암호화 후 엔티티 반영
        String encodedPassword = passwordEncoder.encode(tempPassword);
        user.updatePassword(encodedPassword);

        // 4. 메일 전송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(requestDto.getEmail());
        message.setSubject("[POPQ 플랫폼] 요청하신 임시 비밀번호 안내");
        message.setText("안녕하세요,\n\n요청하신 임시 비밀번호는 다음과 같습니다:\n\n" + tempPassword + "\n\n로그인 후 반드시 비밀번호를 변경해 주세요.");

        javaMailSender.send(message);
    }
}