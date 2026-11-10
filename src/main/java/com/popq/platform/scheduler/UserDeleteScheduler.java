package com.popq.platform.scheduler;

import com.popq.platform.entity.User;
import com.popq.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDeleteScheduler {

    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredWithdrawnUsers() {
        LocalDateTime targetDate = LocalDateTime.now().minusDays(30);

        // 1. 명시적 JPQL을 통한 조건 조회
        List<User> expiredUsers = userRepository.findExpiredUsers(User.UserStatus.WITHDRAWN, targetDate);

        // 2. 조회된 회원이 있다면 삭제
        if (!expiredUsers.isEmpty()) {
            userRepository.deleteAll(expiredUsers);
            log.info("[회원 정리 스케줄러] 유예 기간이 지난 탈퇴 회원 {}명을 영구 삭제(Hard Delete)했습니다.", expiredUsers.size());
        } else {
            log.info("[회원 정리 스케줄러] 조건에 맞는 유예 만료 회원이 없습니다. (삭제 대상 0명)");
        }
    }
}