package com.popq.platform.auth;

import com.popq.platform.auth.config.JwtTokenProvider;
import com.popq.platform.auth.dto.LoginRequest;
import com.popq.platform.auth.dto.PasswordFindRequest;
import com.popq.platform.auth.dto.SignupRequest;
import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private SignupRequest createSignupRequest(String username, String password) {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "username", username);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "name", "테스터");
        ReflectionTestUtils.setField(request, "phone", "010-1234-5678");
        return request;
    }

    private LoginRequest createLoginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private User createActiveUser(Long id, String username, String encodedPassword, Role role) {
        User user = User.builder()
                .username(username)
                .password(encodedPassword)
                .name("테스터")
                .phone("010-1234-5678")
                .role(role)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getVerificationStore() {
        return (Map<String, Object>) ReflectionTestUtils.getField(authService, "verificationStore");
    }

    private Object createVerificationInfo(String code, LocalDateTime expiryTime) throws Exception {
        Class<?> recordClass = Class.forName("com.popq.platform.auth.service.AuthService$VerificationInfo");
        var ctor = recordClass.getDeclaredConstructor(String.class, LocalDateTime.class);
        ctor.setAccessible(true);
        return ctor.newInstance(code, expiryTime);
    }

    @Test
    @DisplayName("회원가입 성공 시 비밀번호를 암호화하고 권한은 USER로 저장한다")
    void signup_success_alwaysUserRole() {
        SignupRequest request = createSignupRequest("manager01@gmail.com", "Test1234!");
        given(userRepository.existsByUsername("manager01@gmail.com")).willReturn(false);
        given(passwordEncoder.encode("Test1234!")).willReturn("ENCODED_PW");

        authService.signup(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("manager01@gmail.com");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("ENCODED_PW");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("이미 사용 중인 이메일로 회원가입 시 예외가 발생한다")
    void signup_fail_duplicateEmail() {
        SignupRequest request = createSignupRequest("tester01@gmail.com", "Test1234!");
        given(userRepository.existsByUsername("tester01@gmail.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 이메일입니다.");

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("로그인 성공 시 JWT 토큰을 반환한다")
    void login_success() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        LoginRequest request = createLoginRequest("tester01@gmail.com", "Test1234!");

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Test1234!", "ENCODED_PW")).willReturn(true);
        given(jwtTokenProvider.createToken("tester01@gmail.com", "USER")).willReturn("mock.jwt.token");

        String token = authService.login(request);

        assertThat(token).isEqualTo("mock.jwt.token");
    }

    @Test
    @DisplayName("비밀번호 재설정 인증 코드 발송 시 메일을 전송하고 인증 정보를 저장한다")
    void sendPasswordResetCode_success() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        PasswordFindRequest request = new PasswordFindRequest();
        request.setEmail("tester01@gmail.com");
        request.setName("테스터");

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));

        authService.sendPasswordResetCode(request);

        then(javaMailSender).should().send(any(SimpleMailMessage.class));
        assertThat(getVerificationStore()).containsKey("tester01@gmail.com");
    }

    @Test
    @DisplayName("이메일과 이름이 일치하지 않으면 인증 코드 발송 시 예외가 발생한다")
    void sendPasswordResetCode_fail_nameMismatch() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        PasswordFindRequest request = new PasswordFindRequest();
        request.setEmail("tester01@gmail.com");
        request.setName("다른이름");

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.sendPasswordResetCode(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이메일과 이름이 일치하지 않습니다.");

        then(javaMailSender).should(never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("유효한 인증 코드로 비밀번호 재설정 시 새 비밀번호로 변경되고 인증 정보가 삭제된다")
    void resetPassword_success() throws Exception {
        User user = createActiveUser(1L, "tester01@gmail.com", "OLD_PW", Role.USER);
        getVerificationStore().put("tester01@gmail.com",
                createVerificationInfo("123456", LocalDateTime.now().plusMinutes(3)));

        PasswordFindRequest request = new PasswordFindRequest();
        request.setEmail("tester01@gmail.com");
        request.setCode("123456");
        request.setNewPassword("NewPass123!");

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("NewPass123!")).willReturn("NEW_ENCODED_PW");

        authService.resetPassword(request);

        assertThat(user.getPassword()).isEqualTo("NEW_ENCODED_PW");
        assertThat(getVerificationStore()).doesNotContainKey("tester01@gmail.com");
    }

    @Test
    @DisplayName("만료된 인증 코드로 비밀번호 재설정 시 예외가 발생하고 저장소에서 제거된다")
    void resetPassword_fail_expiredCode() throws Exception {
        getVerificationStore().put("tester01@gmail.com",
                createVerificationInfo("123456", LocalDateTime.now().minusSeconds(1)));

        PasswordFindRequest request = new PasswordFindRequest();
        request.setEmail("tester01@gmail.com");
        request.setCode("123456");
        request.setNewPassword("NewPass123!");

        assertThatThrownBy(() -> authService.resetPassword(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("인증 코드가 만료되었습니다. 다시 요청해 주세요.");

        assertThat(getVerificationStore()).doesNotContainKey("tester01@gmail.com");
    }
}