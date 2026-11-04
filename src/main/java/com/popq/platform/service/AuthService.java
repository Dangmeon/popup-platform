package com.popq.platform.service;

import com.popq.platform.dto.PasswordFindRequest;
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
    public void findAndSendTempPassword(PasswordFindRequest requestDto) {
        // 1. 가입된 사용자 확인
        User user = userRepository.findByUsername(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // 2. 8자리 랜덤 임시 비밀번호 생성
        String tempPassword = UUID.randomUUID().toString().substring(0, 8);

        // 3. 비밀번호 암호화 후 엔티티 반영
        String encodedPassword = passwordEncoder.encode(tempPassword);
        user.updatePassword(encodedPassword);

        // 4. 실제로 메일 전송하기
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(requestDto.getEmail()); // 수신자 이메일
        message.setSubject("[POPQ 플랫폼] 요청하신 임시 비밀번호 안내"); // 메일 제목
        message.setText("안녕하세요,\n\n요청하신 임시 비밀번호는 다음과 같습니다:\n\n" + tempPassword + "\n\n로그인 후 반드시 비밀번호를 변경해 주세요."); // 메일 내용

        javaMailSender.send(message);
    }
}