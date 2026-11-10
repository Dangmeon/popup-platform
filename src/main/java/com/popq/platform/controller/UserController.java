package com.popq.platform.controller;

import com.popq.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @DeleteMapping("/me")
    public ResponseEntity<String> withdraw(@AuthenticationPrincipal String username) {
        userService.withdraw(username);
        return ResponseEntity.ok("회원탈퇴가 완료되었습니다.");
    }
}