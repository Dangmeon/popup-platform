package com.popq.platform.controller;

import com.popq.platform.config.JwtTokenProvider;
import com.popq.platform.dto.LoginRequestDto;
import com.popq.platform.dto.SignupRequestDto;
import com.popq.platform.entity.User;
import com.popq.platform.repository.UserRepository;
import com.popq.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequestDto requestDto) {
        userService.signup(requestDto);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequestDto requestDto) {
        User user = userRepository.findByUsername(requestDto.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일(아이디)입니다."));

        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 1. 유저의 권한 정보를 가져옵니다.
        String role = user.getRole().name();

        // 2. requestDto에서 username을 가져와 토큰 생성에 전달
        String token = jwtTokenProvider.createToken(requestDto.getUsername(), role);

        // 3. 생성된 토큰을 응답으로 반환
        return ResponseEntity.ok(token);
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("인증 성공! 토큰이 정상적으로 작동합니다.");
    }
}