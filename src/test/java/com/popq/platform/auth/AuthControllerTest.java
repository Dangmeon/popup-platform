package com.popq.platform.auth;

import com.popq.platform.auth.config.JwtTokenProvider;
import com.popq.platform.auth.config.SecurityConfig;
import com.popq.platform.auth.controller.AuthController;
import com.popq.platform.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("유효한 회원가입 요청 시 200 OK와 완료 메시지를 반환한다")
    void signup_success() throws Exception {
        String body = """
                {
                  "username": "tester01@gmail.com",
                  "password": "Test1234!",
                  "name": "테스터",
                  "phone": "010-1234-5678",
                  "role": "USER"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("회원가입이 완료되었습니다."));

        then(authService).should().signup(any());
    }

    @Test
    @DisplayName("로그인 요청 시 발급된 JWT 토큰 문자열을 반환한다")
    void login_success() throws Exception {
        given(authService.login(any())).willReturn("eyJ.mock.jwt.token");

        String body = """
                {
                  "username": "tester01@gmail.com",
                  "password": "Test1234!"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("eyJ.mock.jwt.token"));
    }

    @Test
    @DisplayName("유효한 Bearer 토큰으로 /api/auth/test 호출 시 인증 성공 메시지를 반환한다")
    void authTest_withValidToken_success() throws Exception {
        String token = "valid.jwt.token";
        given(jwtTokenProvider.validateToken(token)).willReturn(true);
        given(jwtTokenProvider.getUsername(token)).willReturn("tester01@gmail.com");
        given(jwtTokenProvider.getRole(token)).willReturn("USER");

        mockMvc.perform(get("/api/auth/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("인증 성공! 토큰이 정상적으로 작동합니다."));
    }
}