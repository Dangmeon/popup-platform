package com.popq.platform.auth;

import com.popq.platform.auth.config.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        // HS256 키 길이(최소 256비트 / 32바이트 이상)를 충족하는 Base64 시크릿
        String rawSecret = "popq-platform-test-secret-key-for-jwt-hs256-2026";
        String base64Secret = Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));
        long expirationMs = 3600000L; // 1시간

        jwtTokenProvider = new JwtTokenProvider(base64Secret, expirationMs);
    }

    @Test
    @DisplayName("토큰 생성 시 username과 role 클레임이 정상 포함되고 유효성 검증을 통과한다")
    void createAndValidateToken_success() {
        String token = jwtTokenProvider.createToken("tester01@gmail.com", "USER");

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsername(token)).isEqualTo("tester01@gmail.com");
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo("USER");
    }

    @Test
    @DisplayName("변조되거나 잘못된 형식의 토큰은 검증에 실패한다")
    void validateToken_fail_whenInvalidToken() {
        String validToken = jwtTokenProvider.createToken("tester01@gmail.com", "USER");
        String tamperedToken = validToken + "tampered";

        assertThat(jwtTokenProvider.validateToken(tamperedToken)).isFalse();
        assertThat(jwtTokenProvider.validateToken("not.a.jwt.token")).isFalse();
    }

    @Test
    @DisplayName("만료 시간이 지난 토큰은 검증에 실패한다")
    void validateToken_fail_whenExpired() throws InterruptedException {
        String rawSecret = "popq-platform-test-secret-key-for-jwt-hs256-2026";
        String base64Secret = Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(base64Secret, 1L);

        String token = shortLivedProvider.createToken("tester01@gmail.com", "USER");
        Thread.sleep(15L);

        assertThat(shortLivedProvider.validateToken(token)).isFalse();
    }
}