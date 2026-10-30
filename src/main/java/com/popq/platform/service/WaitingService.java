package com.popq.platform.service;

import com.popq.platform.dto.WaitingRegisterRequest;
import com.popq.platform.dto.WaitingStatusResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.entity.WaitingQueue;
import com.popq.platform.entity.WaitingStatus;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingService {

    private final WaitingQueueRepository waitingQueueRepository;
    private final PopupStoreRepository popupStoreRepository;
    private final SimpMessagingTemplate messagingTemplate; // Spring이 제공하는 STOMP 메시지 발송 도구(WebSocket 메시지 전송용) 생성

    // 현장 대기열 등록

    @Transactional
    public WaitingStatusResponse registerWaiting(WaitingRegisterRequest request) {

        // 팝업스토어 존재 여부 확인
        PopupStore popup = popupStoreRepository.findByIdWithPessimisticLock(request.getPopId()) // + 비관적 락 팝업 레포에서 정의 필요
                .orElseThrow(() -> new IllegalArgumentException("해당 팝업스토어가 존재하지 않습니다. ID: " + request.getPopId()));

        // 다만 위 코드의 경우 비관적 락을 지금 팝업에 거는 행위임. 지금은 이거 외엔 순서 락을 걸 다른 기준이 없어 어쩔 수 없이 사용
        // 다음과 같은 문제점이 발생할 수 있음
        // 1. 락이 걸리면 관리자 팝업 CRUD에도 영향이 감
        // 2. 사람이 많이 몰릴 때 트랜잭션 내부에 부가 작업이 조금이라도 길어지면 뒤쪽 요청들은 커넥션 풀을 고갈시키며 응답 지연이 발생.
        // 현재 임시로 사용하고 나중에 다른 대안이 필요 1. 별도의 순번 카운터 테이블 분리 / 2. 원자적 갱신 쿼리 / 3. Redis의 INCR 사용(대규모) 등등

        // 팝업 운영 상태 검증 (walkin_status == 'OPEN'인지?)
        if (!"OPEN".equalsIgnoreCase(popup.getWalkinStatus())) {
            throw new IllegalStateException("현재 해당 팝업스토어는 현장 대기 접수가 마감되었거나 중단되었습니다.");
        }

        // 중복 접수 방지 (동일 팝업에 이미 등록되어 WAITING 혹은 CALLED 상태인 전화번호인지?)
        List<WaitingStatus> activeStatuses = List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);
        boolean isAlreadyWaiting = waitingQueueRepository.existsActiveWaiting(
                request.getPopId(), // 팝업이 같은지?
                request.getPhone(), // 휴대폰이 같은지?
                activeStatuses // 호출 or 대기 상태인지?
        ); // return true or false 셋다 true면 true 반환

        if (isAlreadyWaiting) {
            throw new IllegalStateException("이미 대기 접수되었거나 호출 중인 번호입니다.");
        } // 만약 이미 웨이팅중인 번호라면? 예외 던지기

        // 당일 대기번호 처리(당일 자정 00:00:00 이후 기준 MAX 번호 + 1)
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay(); // 당일 00:00:00 시간을 의미,, 이때 대기 번호 최댓값 0으로 초기화
        int nextQueueNumber = waitingQueueRepository.findMaxQueueNumberToday(request.getPopId(), startOfToday)
                .orElse(0) + 1;

        // WaitingQueue 엔티티 생성 및 데이터베이스 저장
        WaitingQueue newWaiting = WaitingQueue.builder()
                .popupStore(popup)
                .phone(request.getPhone())
                .peopleCount(request.getPeopleCount())
                .queueNumber(nextQueueNumber)
                .build();

        WaitingQueue savedWaiting = waitingQueueRepository.save(newWaiting);

        // 내 앞 대기 팀 수 계산 (나보다 작은 번호이면서 현재 순수 WAITING 상태인 팀 수)
        long teamsAhead = waitingQueueRepository.countTeamsAhead(
                request.getPopId(),
                WaitingStatus.WAITING, // WAITING 상태이면서
                savedWaiting.getQueueNumber(),  // 번호
                startOfToday
        );

        // 응답 DTO 생성 (팀당 예상 체류 시간 DEFAULT : 5분)
        WaitingStatusResponse response = WaitingStatusResponse.of(savedWaiting, teamsAhead, 5);

        // 웹소켓
        sendWebSocketNotification(
                savedWaiting.getWaitingId(),
                request.getPopId(),
                response,
                "대기 등록"
        );
        // 반환
        return response;
    }

    // 현재 대기 상태 조회 메소드(조회는 ReadOnly)
    @Transactional(readOnly = true) //readonly
    public WaitingStatusResponse getWaitingStatus(Long waitingId) {

        // 대기열 내역 조회
        WaitingQueue queue = waitingQueueRepository.findById(waitingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 대기열 정보를 찾을 수 없습니다. ID: " + waitingId)); // 없으면 예외 던지기

        Long teamsAhead = null;
        Integer minutesPerTeam = null;

        // 내 앞 대기 팀 수 계산 (기본 null값, 앞 팀이 현재 WAITING 상태인 경우에만 계산)
        if (queue.getWaitingStatus() == WaitingStatus.WAITING) {
            LocalDateTime startOfDay = queue.getJoinedAt().toLocalDate().atStartOfDay();
            teamsAhead = waitingQueueRepository.countTeamsAhead(
                    queue.getPopupStore().getPopId(),
                    WaitingStatus.WAITING,
                    queue.getQueueNumber(),
                    startOfDay
            );
        }

        // 현재 상태 DTO 반환
        return WaitingStatusResponse.of(queue, teamsAhead, 5);
    }

    // 대기열 취소 메소드
    @Transactional
    public WaitingStatusResponse cancelWaiting(Long waitingId) {

        // 대기열 내역 조회
        WaitingQueue waiting = waitingQueueRepository.findById(waitingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 대기열 정보를 찾을 수 없습니다. ID: " + waitingId));

        // 검증 및 CANCELLED로 상태 변경
        waiting.cancel();

        // 취소 상태 응답 DTO 생성 (취소되었으므로 앞 팀 수와 예상 대기 시간은 null로)
        WaitingStatusResponse response = WaitingStatusResponse.of(waiting, null, null);


        // [웹소켓] 취소자 본인 + 관리자 페이지에 변경사항 반영
        sendWebSocketNotification(
                waiting.getWaitingId(),
                waiting.getPopupStore().getPopId(),
                response,
                "대기 취소"
        );
        return response;
    }

    // [관리자용] 대기자 호출 (WAITING -> CALLED로 변경)
    // @param waitingId 대기열 ID
    // @param timeoutMinutes 입장 제한/유효 시간 (예: 10분)
    @Transactional
    public WaitingStatusResponse callWaiting(Long waitingId, int timeoutMinutes) {
        // 대기열 내역 조회
        WaitingQueue waiting = waitingQueueRepository.findById(waitingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 대기열 정보를 찾을 수 없습니다. ID: " + waitingId));

        // 엔티티 상태 전이 (규칙 검증(WAITING 인지?) 및 calledAt, expiresAt 시각 설정)
        waiting.call(timeoutMinutes);

        // 응답 DTO 생성 (호출 상태(앞에 아무도 없는 상태)는 앞 대기 팀 수와 대기 시간은 null 처리)
        WaitingStatusResponse response = WaitingStatusResponse.of(waiting, null, null);

        // [웹소켓]
        // - 고객: 입장 안내 팝업 유도
        // - 관리자: 대기자 리스트 내 상태를 '호출 중'으로 실시간 변경
        sendWebSocketNotification(
                waiting.getWaitingId(),
                waiting.getPopupStore().getPopId(),
                response,
                "대기자 호출"
        );

        return response;
    }

     // [관리자용] 입장 완료 처리 (CALLED / WAITING -> ENTERED)
     // @param waitingId 대기열 ID
    @Transactional
    public WaitingStatusResponse enterWaiting(Long waitingId) {
        // 대기열 내역 조회
        WaitingQueue waiting = waitingQueueRepository.findById(waitingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 대기열 정보를 찾을 수 없습니다. ID: " + waitingId));

        // 엔티티 상태 전이 (규칙 검증(WAITING or CALLED인지?) + enteredAt(추가 한다면) 기록)
        waiting.enter();

        // 응답 DTO 생성
        WaitingStatusResponse response = WaitingStatusResponse.of(waiting, null, null);

        // [웹소켓]
        // - 고객 개인 채널: '입장 완료' 처리
        // - 매장 관리자 채널: 활성 대기자 목록에서 퇴장 처리 (화면에서 제거)
        sendWebSocketNotification(
                waiting.getWaitingId(),
                waiting.getPopupStore().getPopId(),
                response,
                "입장 완료"
        );

        return response;
    }

    // 웹소켓 전달 메소드
    private void sendWebSocketNotification(Long waitingId, Long popId, WaitingStatusResponse response, String actionName) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { // TransactionSynchronizationManager에 의해 DB가 커밋이 되었는지 검증
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { // 커밋 후 전달되도록 콜백함수 예약
                    messagingTemplate.convertAndSend("/topic/waitings/" + waitingId, response);
                    messagingTemplate.convertAndSend("/topic/popups/" + popId + "/waitings", response);
                    log.info("[{}] waitingId: {}, popId: {}", actionName, waitingId, popId); // 로그 출력
                }
            });
        } else { // 테스트 환경 대비 fallback
            messagingTemplate.convertAndSend("/topic/waitings/" + waitingId, response);
            messagingTemplate.convertAndSend("/topic/popups/" + popId + "/waitings", response);
        }
    }
}