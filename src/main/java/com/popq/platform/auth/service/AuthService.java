package com.popq.platform.auth.service;

import com.popq.platform.auth.config.JwtTokenProvider;
import com.popq.platform.auth.dto.LoginRequest;
import com.popq.platform.auth.dto.SignupRequest;
import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.common.dto.PasswordFindRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender;
    private final JwtTokenProvider jwtTokenProvider;

    private final Map<String, VerificationInfo> verificationStore = new ConcurrentHashMap<>();

    private record VerificationInfo(String code, LocalDateTime expiryTime) {
    }

    @Transactional
    public void signup(SignupRequest requestDto) {
        if (userRepository.existsByUsername(requestDto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        Role userRole = Role.USER;
        if (requestDto.getRole() != null && "MANAGER".equalsIgnoreCase(requestDto.getRole().trim())) {
            userRole = Role.MANAGER;
        }

        User user = User.builder()
                .username(requestDto.getUsername())
                .password(encodedPassword)
                .name(requestDto.getName())
                .phone(requestDto.getPhone())
                .role(userRole)
                .build();

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public String login(LoginRequest requestDto) {
        User user = userRepository.findByUsername(requestDto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));

        if (user.getStatus() == User.UserStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴한 회원입니다.");
        }

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        String roleName = (user.getRole() != null) ? user.getRole().name() : Role.USER.name();
        return jwtTokenProvider.createToken(user.getUsername(), roleName);
    }

    public void sendPasswordResetCode(PasswordFindRequest requestDto) {
        User user = userRepository.findByUsername(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        if (!user.getName().equals(requestDto.getName())) {
            throw new IllegalArgumentException("이메일과 이름이 일치하지 않습니다.");
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(3);

        verificationStore.put(requestDto.getEmail(), new VerificationInfo(code, expiryTime));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(requestDto.getEmail());
        message.setSubject("[POPQ 플랫폼] 비밀번호 재설정 인증 코드 안내");
        message.setText("안녕하세요, " + user.getName() + "님\n\n요청하신 비밀번호 재설정 인증 코드는 다음과 같습니다:\n\n[ " + code + " ]\n\n3분 이내에 입력해 주세요.");

        javaMailSender.send(message);
    }

    @Transactional
    public void resetPassword(PasswordFindRequest requestDto) {
        VerificationInfo info = verificationStore.get(requestDto.getEmail());

        if (info == null) {
            throw new IllegalArgumentException("인증 코드를 요청하지 않았거나 만료되었습니다.");
        }

        if (LocalDateTime.now().isAfter(info.expiryTime())) {
            verificationStore.remove(requestDto.getEmail());
            throw new IllegalArgumentException("인증 코드가 만료되었습니다. 다시 요청해 주세요.");
        }

        if (!info.code().equals(requestDto.getCode())) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않습니다.");
        }

        User user = userRepository.findByUsername(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        String encodedPassword = passwordEncoder.encode(requestDto.getNewPassword());
        user.updatePassword(encodedPassword);
        verificationStore.remove(requestDto.getEmail());
    }
}