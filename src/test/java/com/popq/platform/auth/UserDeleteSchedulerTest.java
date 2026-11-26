package com.popq.platform.auth;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.auth.scheduler.UserDeleteScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class UserDeleteSchedulerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDeleteScheduler userDeleteScheduler;

    @Test
    @DisplayName("30일 유예 기간이 지난 WITHDRAWN 회원이 있으면 deleteAll을 호출한다")
    void cleanupExpiredWithdrawnUsers_deletesExpiredUsers() {
        User expiredUser = User.builder()
                .username("old@gmail.com")
                .password("PW")
                .name("탈퇴자")
                .phone("010-0000-0000")
                .role(Role.USER)
                .build();
        expiredUser.withdraw();

        given(userRepository.findExpiredUsers(eq(User.UserStatus.WITHDRAWN), any(LocalDateTime.class)))
                .willReturn(List.of(expiredUser));

        userDeleteScheduler.cleanupExpiredWithdrawnUsers();

        then(userRepository).should().deleteAll(List.of(expiredUser));
    }

    @Test
    @DisplayName("삭제 대상 회원이 없으면 deleteAll을 호출하지 않는다")
    void cleanupExpiredWithdrawnUsers_noTargetUsers() {
        given(userRepository.findExpiredUsers(eq(User.UserStatus.WITHDRAWN), any(LocalDateTime.class)))
                .willReturn(List.of());

        userDeleteScheduler.cleanupExpiredWithdrawnUsers();

        then(userRepository).should(never()).deleteAll(any());
    }
}