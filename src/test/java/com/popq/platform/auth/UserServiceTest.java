package com.popq.platform.auth;

import com.popq.platform.auth.config.JwtTokenProvider;
import com.popq.platform.auth.dto.LoginRequest;
import com.popq.platform.auth.dto.MemberInfoResponse;
import com.popq.platform.auth.dto.MemberUpdateRequest;
import com.popq.platform.auth.dto.SignupRequest;
import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.auth.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserService userService;

    private SignupRequest createSignupRequest(String username, String password) {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "username", username);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "name", "테스터");
        ReflectionTestUtils.setField(request, "phone", "010-1234-5678");
        ReflectionTestUtils.setField(request, "role", "USER");
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
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 11, 26, 10, 0));
        return user;
    }

    @Test
    @DisplayName("회원가입 성공 시 비밀번호 암호화 후 USER 권한으로 저장한다")
    void signup_success() {
        SignupRequest request = createSignupRequest("tester01@gmail.com", "Test1234!");
        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.empty());
        given(passwordEncoder.encode("Test1234!")).willReturn("ENCODED_PW");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        userService.signup(request);

        then(passwordEncoder).should().encode("Test1234!");
        then(userRepository).should().save(any(User.class));
    }

    @Test
    @DisplayName("이미 존재하는 username으로 회원가입 시 IllegalArgumentException이 발생한다")
    void signup_fail_duplicateUsername() {
        SignupRequest request = createSignupRequest("tester01@gmail.com", "Test1234!");
        User existingUser = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 존재하는 아이디입니다.");

        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("정상 로그인 시 JWT 토큰을 발급해 반환한다")
    void login_success() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        LoginRequest request = createLoginRequest("tester01@gmail.com", "Test1234!");

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Test1234!", "ENCODED_PW")).willReturn(true);
        given(jwtTokenProvider.createToken("tester01@gmail.com", "USER")).willReturn("jwt.token.value");

        String token = userService.login(request);

        assertThat(token).isEqualTo("jwt.token.value");
    }

    @Test
    @DisplayName("존재하지 않는 이메일로 로그인 시 예외가 발생한다")
    void login_fail_userNotFound() {
        LoginRequest request = createLoginRequest("missing@gmail.com", "Test1234!");
        given(userRepository.findByUsername("missing@gmail.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("존재하지 않는 이메일(아이디)입니다.");
    }

    @Test
    @DisplayName("탈퇴(WITHDRAWN) 상태인 회원 로그인 시 예외가 발생한다")
    void login_fail_withdrawnUser() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        user.withdraw();
        LoginRequest request = createLoginRequest("tester01@gmail.com", "Test1234!");

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("탈퇴한 회원입니다.");
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 로그인 시 예외가 발생한다")
    void login_fail_wrongPassword() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        LoginRequest request = createLoginRequest("tester01@gmail.com", "Wrong1234!");

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Wrong1234!", "ENCODED_PW")).willReturn(false);

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
    }

    @Test
    @DisplayName("내 정보 조회 시 MemberInfoResponse를 반환한다")
    void getMyInfo_success() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));

        MemberInfoResponse response = userService.getMyInfo("tester01@gmail.com");

        assertThat(response.getMemberId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("tester01@gmail.com");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("내 정보 및 비밀번호 수정 시 이름·전화번호와 암호화된 비밀번호가 반영된다")
    void updateMyInfo_success() {
        User user = createActiveUser(1L, "tester01@gmail.com", "OLD_ENCODED_PW", Role.USER);
        MemberUpdateRequest updateRequest = new MemberUpdateRequest();
        updateRequest.setName("새이름");
        updateRequest.setPhone("010-9999-8888");
        updateRequest.setPassword("NewPass123!");

        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));
        given(passwordEncoder.encode("NewPass123!")).willReturn("NEW_ENCODED_PW");

        MemberInfoResponse response = userService.updateMyInfo("tester01@gmail.com", updateRequest);

        assertThat(response.getName()).isEqualTo("새이름");
        assertThat(response.getPhone()).isEqualTo("010-9999-8888");
        assertThat(user.getPassword()).isEqualTo("NEW_ENCODED_PW");
    }

    @Test
    @DisplayName("회원탈퇴 시 상태가 WITHDRAWN으로 변경되고 탈퇴 일시가 기록된다")
    void withdraw_success() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));

        userService.withdraw("tester01@gmail.com");

        assertThat(user.getStatus()).isEqualTo(User.UserStatus.WITHDRAWN);
        assertThat(user.getWithdrawnAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 탈퇴한 회원이 다시 탈퇴를 요청하면 예외가 발생한다")
    void withdraw_fail_alreadyWithdrawn() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        user.withdraw();
        given(userRepository.findByUsername("tester01@gmail.com")).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.withdraw("tester01@gmail.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 탈퇴 처리된 회원입니다.");
    }

    @Test
    @DisplayName("전체 회원 페이징 조회 시 MemberInfoResponse 페이지로 변환된다")
    void getAllMembers_success() {
        User user = createActiveUser(1L, "tester01@gmail.com", "ENCODED_PW", Role.USER);
        PageRequest pageable = PageRequest.of(0, 10);
        given(userRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<MemberInfoResponse> result = userService.getAllMembers(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("tester01@gmail.com");
    }
}