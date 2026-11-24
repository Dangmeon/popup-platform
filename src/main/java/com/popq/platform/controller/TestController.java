package com.popq.platform.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/api/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("인증 성공! 토큰이 정상적으로 작동합니다.");
    }
}