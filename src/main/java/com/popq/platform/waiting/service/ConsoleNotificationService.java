package com.popq.platform.waiting.service;

import com.popq.platform.waiting.event.WaitingCalledEvent;
import com.popq.platform.waiting.event.WaitingRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConsoleNotificationService implements NotificationService {

    // 일단은 로그로 대체

    @Override
    public void sendRegistrationNotification(WaitingRegisteredEvent event) {
        log.info("""
                
                ============================================================
                [SMS/알림톡 발송 - 대기 접수 완료]
                - 수신자: {}
                - 매장명: {}
                - 대기번호: {}번
                - 내 앞 대기팀: {}팀
                - 안내: 고객님의 대기 접수가 정상 완료되었습니다. 차례가 되면 다시 안내해 드리겠습니다.
                ============================================================""",
                event.phone(),
                event.popupName(),
                event.queueNumber(),
                event.teamsAhead()
        );
    }

    @Override
    public void sendCallNotification(WaitingCalledEvent event) {
        log.info("""
                
                ============================================================
                [SMS/알림톡 발송 - 입장 호출]
                - 수신자: {}
                - 매장명: {}
                - 대기번호: {}번
                - 체크인 코드: {}
                - 안내: 지금 바로 매장 입구로 와주세요! (호출 후 10분 내 미방문 시 대기가 자동 취소됩니다.)
                - 입장 QR 확인 링크: http://localhost:5173/waiting/{}
                ============================================================""",
                event.phone(),
                event.popupName(),
                event.queueNumber(),
                event.checkInCode(),
                event.waitingId() // 프론트에서 체크인 코드 qr로 변경후 위 주소에 띄우게끔 하면 될 거 같습니다.
        );
    }
}