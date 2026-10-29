package com.popq.platform.controller;

import com.popq.platform.dto.LoginRequest;
import com.popq.platform.dto.PasswordFindRequest;
import com.popq.platform.dto.SignupRequest;
import com.popq.platform.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody @Valid SignupRequest requestDto) {
        userService.signup(requestDto);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody @Valid LoginRequest requestDto) {
        String token = userService.login(requestDto);
        return ResponseEntity.ok(token);
    }

    @PostMapping("/find-password")
    public ResponseEntity<String> findPassword(@RequestBody @Valid PasswordFindRequest requestDto) {
        userService.findPassword(requestDto);
        return ResponseEntity.ok("임시 비밀번호가 발급되었습니다.");
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("인증 성공! 토큰이 정상적으로 작동합니다.");
    }
}