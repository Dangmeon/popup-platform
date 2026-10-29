package com.popq.platform.service;

import com.popq.platform.config.JwtTokenProvider;
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
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JavaMailSender mailSender;

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

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // role이 null일 경우 방어 처리 (기본값 USER)
        String roleName = (user.getRole() != null) ? user.getRole().name() : Role.USER.name();

        return jwtTokenProvider.createToken(user.getUsername(), roleName);
    }

    @Transactional
    public void findPassword(PasswordFindRequest requestDto) {
        User user = userRepository.findByUsername(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 가입된 사용자가 없습니다."));

        // 2. 8자리 랜덤 임시 비밀번호 생성 (UUID 활용)
        String tempPassword = UUID.randomUUID().toString().substring(0, 8);

        // 3. 비밀번호 암호화 후 업데이트
        String encodedPassword = passwordEncoder.encode(tempPassword);
        user.updatePassword(encodedPassword);

        // 4. 실제로 메일 전송하기
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(requestDto.getEmail());
        message.setSubject("[POP-Q] 요청하신 임시 비밀번호가 발급되었습니다.");
        message.setText("안녕하세요,\n\n요청하신 임시 비밀번호는 [ " + tempPassword + " ] 입니다.\n로그인 후 반드시 비밀번호를 변경해 주세요.");

        mailSender.send(message);
    }
}