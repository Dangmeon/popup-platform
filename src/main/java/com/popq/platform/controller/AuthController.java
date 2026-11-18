package com.popq.platform.controller;

import com.popq.platform.dto.LoginRequest;
import com.popq.platform.dto.PasswordFindRequest;
import com.popq.platform.dto.SignupRequest;
import com.popq.platform.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody @Valid SignupRequest requestDto) {
        authService.signup(requestDto);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody @Valid LoginRequest requestDto) {
        String token = authService.login(requestDto);
        return ResponseEntity.ok(token);
    }

    // 이메일 + 이름 검증 후 인증 코드 발송
    @PostMapping("/password/code")
    public ResponseEntity<String> sendPasswordResetCode(@RequestBody @Valid PasswordFindRequest requestDto) {
        authService.sendPasswordResetCode(requestDto);
        return ResponseEntity.ok("인증 코드가 이메일로 발송되었습니다.");
    }

    // 인증 코드 검증 및 새 비밀번호로 변경
    @PutMapping("/password/reset")
    public ResponseEntity<String> resetPassword(@RequestBody @Valid PasswordFindRequest requestDto) {
        authService.resetPassword(requestDto);
        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("인증 성공! 토큰이 정상적으로 작동합니다.");
    }
}