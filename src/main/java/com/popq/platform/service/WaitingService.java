package com.popq.platform.service;

import com.popq.platform.dto.WaitingRegisterRequest;
import com.popq.platform.dto.WaitingStatusResponse;
import com.popq.platform.entity.PopupStore;
import com.popq.platform.entity.WaitingQueue;
import com.popq.platform.entity.WaitingStatus;
import com.popq.platform.repository.PopupStoreRepository;
import com.popq.platform.repository.WaitingQueueRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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

        // 팝업 운영 상태 검증 (walkin_status == 'OPEN'인지?)
        if (!"OPEN".equalsIgnoreCase(popup.getWalkinStatus())) {
            throw new IllegalStateException("현재 해당 팝업스토어는 현장 대기 접수가 마감되었거나 중단되었습니다.");
        } // 팝업 엔티티 생성 및 명칭 통일 필요 (현재 오류 있음)

        // 중복 접수 방지 (동일 팝업에 이미 등록되어 WAITING 혹은 CALLED 상태인 전화번호인지?)
        List<WaitingStatus> activeStatuses = List.of(WaitingStatus.WAITING, WaitingStatus.CALLED);
        boolean isAlreadyWaiting = waitingQueueRepository.existsByPopupStorePopIdAndPhoneAndWaitingStatusIn(
                request.getPopId(),
                request.getPhone(),
                activeStatuses
        ); // return true or false

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
                savedWaiting.getQueueNumber()  // 번호
        );

        // 응답 DTO 생성 (팀당 예상 체류 시간 DEFAULT : 5분)
        WaitingStatusResponse response = WaitingStatusResponse.of(savedWaiting, teamsAhead, 5);

        // [웹소켓] 실시간 갱신
        // [웹소켓] 고객용 개별 메시지
        messagingTemplate.convertAndSend("/topic/waitings/" + savedWaiting.getWaitingId(), response);
        // [웹소켓] 팝업 스토어에서 고객을 받을 때 받는 매니저/관리자용 메시지
        messagingTemplate.convertAndSend("/topic/popups/" + request.getPopId() + "/waitings", response);

        log.info("[WebSocket] 대기열 등록 실시간 메시지 발송 완료 - waitingId: {}, popId: {}",
                savedWaiting.getWaitingId(), request.getPopId());
        // 반환
        return response;
    }

    // 현재 대기 상태 조회 메소드
    @Transactional //readonly
    public WaitingStatusResponse getWaitingStatus(Long waitingId) {

        // 대기열 내역 조회
        WaitingQueue queue = waitingQueueRepository.findById(waitingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 대기열 정보를 찾을 수 없습니다. ID: " + waitingId)); // 없으면 예외 던지기

        // 내 앞 대기 팀 수 계산 (기본 0팀, 앞 팀이 현재 WAITING 상태인 경우에만 계산)
        long teamsAhead = 0;
        if (queue.getWaitingStatus() == WaitingStatus.WAITING) {
            teamsAhead = waitingQueueRepository.countTeamsAhead(
                    queue.getPopupStore().getPopId(),
                    WaitingStatus.WAITING,
                    queue.getQueueNumber()
            );
        }

        // 현재 상태 DTO 반환
        return WaitingStatusResponse.of(queue, teamsAhead, 5);
    }
}