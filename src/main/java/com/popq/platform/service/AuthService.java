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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender javaMailSender;
    private final JwtTokenProvider jwtTokenProvider;

    // 이메일별 인증 코드와 만료 시간을 임시 저장하는 메모리 저장소 (3분 유효)
    private final Map<String, VerificationInfo> verificationStore = new ConcurrentHashMap<>();

    private static class VerificationInfo {
        String code;
        LocalDateTime expiryTime;

        public VerificationInfo(String code, LocalDateTime expiryTime) {
            this.code = code;
            this.expiryTime = expiryTime;
        }
    }

    @Transactional
    public void signup(SignupRequest requestDto) {
        if (userRepository.existsByUsername(requestDto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        Role userRole = Role.USER;
        if (requestDto.getRole() != null && requestDto.getRole().trim().toUpperCase().equals("MANAGER")) {
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

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // role 정보 가져오기 (null 방어 처리)
        String roleName = (user.getRole() != null) ? user.getRole().name() : Role.USER.name();

        // 실제 JWT 토큰 생성 및 반환
        return jwtTokenProvider.createToken(user.getUsername(), roleName);
    }

    // 이메일 + 이름 검증 후 6자리 인증 코드 발송
    public void sendPasswordResetCode(PasswordFindRequest requestDto) {
        User user = userRepository.findByUsername(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        if (!user.getName().equals(requestDto.getName())) {
            throw new IllegalArgumentException("이메일과 이름이 일치하지 않습니다.");
        }

        // 6자리 난수 인증 코드 생성
        String code = String.format("%06d", new Random().nextInt(1000000));

        // 유효 기간 설정 (3분)
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(3);

        verificationStore.put(requestDto.getEmail(), new VerificationInfo(code, expiryTime));

        // 메일 전송
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(requestDto.getEmail());
        message.setSubject("[POPQ 플랫폼] 비밀번호 재설정 인증 코드 안내");
        message.setText("안녕하세요, " + user.getName() + "님\n\n요청하신 비밀번호 재설정 인증 코드는 다음과 같습니다:\n\n[ " + code + " ]\n\n3분 이내에 입력해 주세요.");

        javaMailSender.send(message);
    }

    // 인증 코드 검증 및 새 비밀번호로 변경
    @Transactional
    public void resetPassword(PasswordFindRequest requestDto) {
        VerificationInfo info = verificationStore.get(requestDto.getEmail());

        if (info == null) {
            throw new IllegalArgumentException("인증 코드를 요청하지 않았거나 만료되었습니다.");
        }

        if (LocalDateTime.now().isAfter(info.expiryTime)) {
            verificationStore.remove(requestDto.getEmail());
            throw new IllegalArgumentException("인증 코드가 만료되었습니다. 다시 요청해 주세요.");
        }

        if (!info.code.equals(requestDto.getCode())) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않습니다.");
        }

        User user = userRepository.findByUsername(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        String encodedPassword = passwordEncoder.encode(requestDto.getNewPassword());
        user.updatePassword(encodedPassword);
    }
}