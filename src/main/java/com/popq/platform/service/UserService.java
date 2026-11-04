package com.popq.platform.service;

import com.popq.platform.config.JwtTokenProvider;
import com.popq.platform.dto.*;
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

        // 이름 및 전화번호 수정 (Entity에 해당 메서드가 구현되어 있어야 합니다)
        user.updateMemberInfo(requestDto.getName(), requestDto.getPhone());

        // 비밀번호 수정 요청이 있는 경우 암호화 후 반영
        if (requestDto.getPassword() != null && !requestDto.getPassword().isBlank()) {
            String encodedPassword = passwordEncoder.encode(requestDto.getPassword());
            user.updatePassword(encodedPassword);
        }

        // 수정된 회원 정보를 담아 응답 DTO 반환
        return MemberInfoResponse.from(user);
    }
}