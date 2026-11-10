package com.popq.platform.scheduler;

import com.popq.platform.service.WaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingScheduler {

    private final WaitingService waitingService;

    /**
     * 호출 후 입장 유효 시간 초과된건 자동 만료 처리
     * 1분마다 주기적 실행
     */
    @Scheduled(fixedDelay = 60000) // 60000ms 1분
    public void checkAndExpireWaitings() {
        LocalDateTime now = LocalDateTime.now();
        waitingService.expireWaitings(now);
    }
}