package com.popq.platform.waiting;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.exception.PopupErrorCode;
import com.popq.platform.popup.exception.PopupException;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.waiting.dto.AdminWaitingListResponse;
import com.popq.platform.waiting.dto.CheckInRequest;
import com.popq.platform.waiting.dto.WaitingRegisterRequest;
import com.popq.platform.waiting.dto.WaitingStatusResponse;
import com.popq.platform.waiting.entity.WaitingQueue;
import com.popq.platform.waiting.entity.WaitingStatus;
import com.popq.platform.waiting.exception.WaitingErrorCode;
import com.popq.platform.waiting.exception.WaitingException;
import com.popq.platform.waiting.repository.WaitingQueueRepository;
import com.popq.platform.waiting.service.WaitingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WaitingServiceIntegrationTest {

    @Autowired
    private WaitingService waitingService;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private PopupStoreRepository popupStoreRepository;

    @Autowired
    private UserRepository userRepository;

    private User userA;
    private User userB;
    private User manager1;
    private User manager2;
    private User admin;
    private PopupStore openPopupByManager1;
    private PopupStore pausedPopupByManager2;

    @BeforeEach
    void setUp() {
        userA = userRepository.save(User.builder()
                .username("junit_wait_usera@test.com")
                .password("encodedPw")
                .name("테스터A")
                .phone("010-9595-0001")
                .role(Role.USER)
                .build());

        userB = userRepository.save(User.builder()
                .username("junit_wait_userb@test.com")
                .password("encodedPw")
                .name("테스터B")
                .phone("010-9595-0002")
                .role(Role.USER)
                .build());

        manager1 = userRepository.save(User.builder()
                .username("junit_wait_manager1@test.com")
                .password("encodedPw")
                .name("매니저1")
                .phone("010-9595-0003")
                .role(Role.MANAGER)
                .build());

        manager2 = userRepository.save(User.builder()
                .username("junit_wait_manager2@test.com")
                .password("encodedPw")
                .name("매니저2")
                .phone("010-9595-0004")
                .role(Role.MANAGER)
                .build());

        admin = userRepository.save(User.builder()
                .username("junit_wait_admin@test.com")
                .password("encodedPw")
                .name("총괄관리자")
                .phone("010-9595-0005")
                .role(Role.ADMIN)
                .build());

        openPopupByManager1 = popupStoreRepository.save(PopupStore.builder()
                .title("JUnit 대기열 테스트 팝업1")
                .location("서울 성동구")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(7))
                .category("GOODS")
                .opStatus("OPEN")
                .manager(manager1)
                .build());

        pausedPopupByManager2 = popupStoreRepository.save(PopupStore.builder()
                .title("JUnit 대기열 테스트 팝업2")
                .location("서울 마포구")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(7))
                .category("FASHION")
                .opStatus("PAUSED")
                .manager(manager2)
                .build());
    }

    @AfterEach
    void tearDown() {
        List<Long> testPopIds = new java.util.ArrayList<>();
        if (openPopupByManager1 != null) testPopIds.add(openPopupByManager1.getPopId());
        if (pausedPopupByManager2 != null) testPopIds.add(pausedPopupByManager2.getPopId());

        if (!testPopIds.isEmpty()) {
            List<WaitingQueue> createdWaitings = waitingQueueRepository.findAll().stream()
                    .filter(w -> testPopIds.contains(w.getPopupStore().getPopId()))
                    .toList();
            waitingQueueRepository.deleteAllInBatch(createdWaitings);
        }

        if (openPopupByManager1 != null) popupStoreRepository.delete(openPopupByManager1);
        if (pausedPopupByManager2 != null) popupStoreRepository.delete(pausedPopupByManager2);

        List<User> createdUsers = java.util.stream.Stream.of(userA, userB, manager1, manager2, admin)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!createdUsers.isEmpty()) {
            userRepository.deleteAllInBatch(createdUsers);
        }
    }

    @Test
    @DisplayName("1. 대기 등록 -> 호출(CALLED) -> QR 체크인(ENTERED) 전체 정상 상태 전이 검증")
    void fullWaitingLifecycle_Success() {
        // 1) 대기 등록 (DTO에 다른 memberId를 넣어도 토큰 기준 userA가 매핑되어야 함)
        WaitingRegisterRequest request = new WaitingRegisterRequest(
                openPopupByManager1.getPopId(), 99999L, "010-1111-0001", 2
        );
        WaitingStatusResponse registered = waitingService.registerWaiting(userA.getUsername(), request);

        assertThat(registered.getStatus()).isEqualTo(WaitingStatus.WAITING);
        assertThat(registered.getQueueNumber()).isEqualTo(1);
        assertThat(registered.getTeamsAhead()).isEqualTo(0L);

        WaitingQueue savedEntity = waitingQueueRepository.findById(registered.getWaitingId()).orElseThrow();
        assertThat(savedEntity.getUser().getId()).isEqualTo(userA.getId());

        // 2) 담당 매니저(manager1) 호출
        WaitingStatusResponse called = waitingService.callWaiting(
                manager1.getUsername(), registered.getWaitingId(), 10
        );
        assertThat(called.getStatus()).isEqualTo(WaitingStatus.CALLED);
        assertThat(called.getCheckInCode()).isNotBlank();

        // 3) 담당 매니저(manager1) QR 체크인 완료
        WaitingStatusResponse entered = waitingService.checkIn(
                manager1.getUsername(), new CheckInRequest(called.getCheckInCode())
        );
        assertThat(entered.getStatus()).isEqualTo(WaitingStatus.ENTERED);
    }

    @Test
    @DisplayName("2. 운영 상태가 OPEN이 아닌 팝업에는 현장 대기 등록이 차단된다")
    void registerWaiting_Fail_WhenPopupNotOpen() {
        WaitingRegisterRequest request = new WaitingRegisterRequest(
                pausedPopupByManager2.getPopId(), null, "010-1111-0001", 2
        );

        assertThatThrownBy(() -> waitingService.registerWaiting(userA.getUsername(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("마감되었거나 중단");
    }

    @Test
    @DisplayName("3. 동일 팝업에 이미 WAITING/CALLED 상태인 전화번호로 중복 접수할 수 없다")
    void registerWaiting_Fail_DuplicatePhone() {
        WaitingRegisterRequest request = new WaitingRegisterRequest(
                openPopupByManager1.getPopId(), null, "010-1111-0001", 2
        );
        waitingService.registerWaiting(userA.getUsername(), request);

        assertThatThrownBy(() -> waitingService.registerWaiting(userB.getUsername(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 대기 접수");
    }

    @Test
    @DisplayName("4. IDOR 방어: 타인의 대기 상태를 조회하거나 취소하면 403 FORBIDDEN_WAITING_ACCESS가 발생한다")
    void accessOtherUsersWaiting_Fail_Forbidden() {
        WaitingStatusResponse created = waitingService.registerWaiting(
                userA.getUsername(),
                new WaitingRegisterRequest(openPopupByManager1.getPopId(), null, "010-1111-0001", 2)
        );

        // 타인(userB) 조회 차단
        assertThatThrownBy(() -> waitingService.getWaitingStatus(userB.getUsername(), created.getWaitingId()))
                .isInstanceOf(WaitingException.class)
                .extracting("errorCode")
                .isEqualTo(WaitingErrorCode.FORBIDDEN_WAITING_ACCESS);

        // 타인(userB) 취소 차단
        assertThatThrownBy(() -> waitingService.cancelWaiting(userB.getUsername(), created.getWaitingId()))
                .isInstanceOf(WaitingException.class)
                .extracting("errorCode")
                .isEqualTo(WaitingErrorCode.FORBIDDEN_WAITING_ACCESS);
    }

    @Test
    @DisplayName("5. 본인의 대기 취소는 정상 처리되며 취소 후에는 재호출이 불가능하다")
    void cancelOwnWaiting_Success() {
        WaitingStatusResponse created = waitingService.registerWaiting(
                userA.getUsername(),
                new WaitingRegisterRequest(openPopupByManager1.getPopId(), null, "010-1111-0001", 2)
        );

        WaitingStatusResponse cancelled = waitingService.cancelWaiting(userA.getUsername(), created.getWaitingId());
        assertThat(cancelled.getStatus()).isEqualTo(WaitingStatus.CANCELLED);

        assertThatThrownBy(() -> waitingService.callWaiting(manager1.getUsername(), created.getWaitingId(), 10))
                .isInstanceOf(WaitingException.class)
                .extracting("errorCode")
                .isEqualTo(WaitingErrorCode.INVALID_STATUS_FOR_CALL);
    }

    @Test
    @DisplayName("6. 관리자 권한 검증: 다른 매니저 담당 팝업의 대기 목록 조회, 호출, 체크인은 403 POPUP_ACCESS_DENIED 발생")
    void adminWaitingApis_Fail_WhenOtherManagerAccess() {
        WaitingStatusResponse created = waitingService.registerWaiting(
                userA.getUsername(),
                new WaitingRegisterRequest(openPopupByManager1.getPopId(), null, "010-1111-0001", 2)
        );
        WaitingStatusResponse called = waitingService.callWaiting(
                manager1.getUsername(), created.getWaitingId(), 10
        );

        // manager2가 manager1의 팝업 목록 조회 시도 -> 403
        assertThatThrownBy(() -> waitingService.getAdminWaitingList(
                manager2.getUsername(), openPopupByManager1.getPopId(), null))
                .isInstanceOf(PopupException.class)
                .extracting("errorCode")
                .isEqualTo(PopupErrorCode.POPUP_ACCESS_DENIED);

        // manager2가 manager1의 팝업 대기자 호출 시도 -> 403
        assertThatThrownBy(() -> waitingService.callWaiting(
                manager2.getUsername(), created.getWaitingId(), 10))
                .isInstanceOf(PopupException.class)
                .extracting("errorCode")
                .isEqualTo(PopupErrorCode.POPUP_ACCESS_DENIED);

        // manager2가 manager1의 팝업 QR 체크인 시도 -> 403
        assertThatThrownBy(() -> waitingService.checkIn(
                manager2.getUsername(), new CheckInRequest(called.getCheckInCode())))
                .isInstanceOf(PopupException.class)
                .extracting("errorCode")
                .isEqualTo(PopupErrorCode.POPUP_ACCESS_DENIED);

        // 총괄 ADMIN은 모든 팝업 조회/체크인 가능
        AdminWaitingListResponse adminList = waitingService.getAdminWaitingList(
                admin.getUsername(), openPopupByManager1.getPopId(), null
        );
        assertThat(adminList.getWaitings()).hasSize(1);
    }

    @Test
    @DisplayName("7. 입장 제한 시간이 초과된 호출 건은 체크인이 거부되고 스케줄러 만료 처리 시 EXPIRED로 전이된다")
    void expiredWaiting_CheckInRejected_And_BatchExpireSuccess() {
        WaitingStatusResponse created = waitingService.registerWaiting(
                userA.getUsername(),
                new WaitingRegisterRequest(openPopupByManager1.getPopId(), null, "010-1111-0001", 2)
        );
        WaitingStatusResponse called = waitingService.callWaiting(
                manager1.getUsername(), created.getWaitingId(), 10
        );

        // 만료 시간을 과거로 강제 조작해 타임아웃 상황 재현
        WaitingQueue entity = waitingQueueRepository.findById(created.getWaitingId()).orElseThrow();
        ReflectionTestUtils.setField(entity, "expiresAt", LocalDateTime.now().minusMinutes(1));
        waitingQueueRepository.saveAndFlush(entity);

        // 만료된 상태로 체크인 시도 -> CHECK_IN_EXPIRED 예외
        assertThatThrownBy(() -> waitingService.checkIn(
                manager1.getUsername(), new CheckInRequest(called.getCheckInCode())))
                .isInstanceOf(WaitingException.class)
                .extracting("errorCode")
                .isEqualTo(WaitingErrorCode.CHECK_IN_EXPIRED);

        // 자동 만료 배치 실행 -> EXPIRED 상태로 전이
        waitingService.expireWaitings(LocalDateTime.now());
        WaitingQueue expiredEntity = waitingQueueRepository.findById(created.getWaitingId()).orElseThrow();
        assertThat(expiredEntity.getWaitingStatus()).isEqualTo(WaitingStatus.EXPIRED);
    }
}