package com.popq.platform.reservation.concurrency;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.reservation.dto.ReservationCreateRequest;
import com.popq.platform.reservation.dto.ReservationResponse;
import com.popq.platform.reservation.entity.Reservation;
import com.popq.platform.reservation.entity.ReservationStatus;
import com.popq.platform.reservation.exception.ReservationErrorCode;
import com.popq.platform.reservation.exception.ReservationException;
import com.popq.platform.reservation.redis.ReservationExpirationService;
import com.popq.platform.reservation.repository.ReservationRepository;
import com.popq.platform.reservation.service.ReservationService;
import com.popq.platform.timeslot.entity.TimeSlot;
import com.popq.platform.timeslot.repository.TimeSlotRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReservationConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationExpirationService reservationExpirationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private PopupStoreRepository popupStoreRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;


    // ================ 테스트 데이터 관리 변수 ====================
    private final List<Long> userIds = new ArrayList<>();

    private Long popId;
    private Long timeSlotId;


    // =============== 테스트 데이터 초기 설정 =================

    @BeforeEach
    void setUp() {

        userIds.clear();

        popId = null;
        timeSlotId = null;

        // 테스트 팝업 생성
        PopupStore popupStore = createPopupStore();

        // 정원 5명인 회차 생성
        createTimeSlot(popupStore, 5);
    }


    // ============= 테스트 데이터 삭제 ================

    @AfterEach
    void tearDown() {

        if (popId != null) {

            List<Reservation> reservations = findTestReservations();

            // 테스트 예약에 해당하는 Redis 키 삭제
            for (Reservation reservation : reservations) {

                String key = "reservation:hold:" + reservation.getReservationId();

                stringRedisTemplate.delete(key);
            }

            // 테스트 예약 삭제
            reservationRepository.deleteAll(reservations);
        }

        // 테스트 회차 삭제
        if (timeSlotId != null) {
            timeSlotRepository.deleteById(timeSlotId);
        }

        // 테스트 팝업 삭제
        if (popId != null) {
            popupStoreRepository.deleteById(popId);
        }

        // 테스트 회원 삭제
        for (Long userId : userIds) {
            userRepository.deleteById(userId);
        }

        userIds.clear();

        popId = null;
        timeSlotId = null;
    }


    // =========== 공통 테스트 데이터 생성 메서드 ===========

    // 테스트 회원 생성
    private User createUser() {

        String uniqueId = UUID.randomUUID().toString();

        // 테스트 회원마다 다른 전화번호 생성
        String phone = "010" + ThreadLocalRandom.current()
                .nextInt(10000000, 100000000);

        User user = User.builder()
                .username("test-" + uniqueId + "@test.com")
                .password("testPassword")
                .name("테스트 회원")
                .phone(phone)
                .role(Role.USER)
                .build();

        User savedUser = userRepository.saveAndFlush(user);

        // 테스트 종료 후 삭제할 회원 ID 저장
        userIds.add(savedUser.getId());

        return savedUser;
    }

    // 테스트 팝업 생성
    private PopupStore createPopupStore() {

        LocalDate today = LocalDate.now();

        PopupStore popupStore = PopupStore.builder()
                .title("동시성 테스트 팝업")
                .location("테스트 장소")
                .startDate(today)
                .endDate(today.plusDays(7))
                .category("ETC")
                .description("예약 동시성 테스트용 팝업")
                .storeImg("test.jpg")
                .roundNum(1)
                .reservStart("14:00")
                .reservEnd("15:00")
                .reservPeopleCap(5)
                .opStatus("OPEN")
                .build();

        PopupStore savedPopup = popupStoreRepository.saveAndFlush(popupStore);

        popId = savedPopup.getPopId();

        return savedPopup;
    }

    // 테스트 회차 생성
    private TimeSlot createTimeSlot(PopupStore popupStore, int capacity) {

        TimeSlot timeSlot = TimeSlot.builder()
                .popupStore(popupStore)
                .slotNum(1)
                .slotDate(LocalDate.now().plusDays(2))
                .startTime("14:00")
                .endTime("15:00")
                .capacity(capacity)
                .reservedCount(0)
                .slotStatus("OPEN")
                .build();

        TimeSlot savedTimeSlot = timeSlotRepository.saveAndFlush(timeSlot);

        timeSlotId = savedTimeSlot.getTimeSlotId();

        return savedTimeSlot;
    }

    // 만료 시간이 지난 HOLD 예약 생성
    private Reservation createExpiredHoldReservation(User user, int peopleCount) {

        return transactionTemplate.execute(status -> {

            // 회차 비관적 락 조회
            TimeSlot timeSlot = timeSlotRepository.findByIdForUpdate(timeSlotId).orElseThrow();

            // 회차 예약 인원 증가
            timeSlot.increaseReservedCount(peopleCount);

            LocalDateTime now = LocalDateTime.now();

            // 만료 시간이 이미 지난 HOLD 예약 생성
            Reservation reservation = Reservation.create(
                    user,
                    timeSlot,
                    peopleCount,
                    now.minusMinutes(15),
                    now.minusMinutes(5)
            );

            // 실제 DB에 예약 저장
            return reservationRepository.saveAndFlush(reservation);
        });
    }

    // 테스트용 팝업에 속한 전체 예약 조회
    private List<Reservation> findTestReservations() {

        return reservationRepository.findByPopupId(popId);
    }


    // ========== 공통 동시 요청 실행 메서드 ==============

    private void executeConcurrently(List<Runnable> tasks) throws Exception {

        int threadCount = tasks.size();

        // 요청 개수만큼 스레드 생성
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        // 모든 스레드의 준비 여부 확인
        CountDownLatch readyLatch = new CountDownLatch(threadCount);

        // 모든 스레드의 시작 시점 제어
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<?>> futures = new ArrayList<>();

        try {
            for (Runnable task : tasks) {

                Future<?> future = executorService.submit(() -> {

                            // 스레드 준비 완료
                            readyLatch.countDown();

                            // 시작 신호가 올 때까지 대기
                            startLatch.await();

                            // 실제 요청 실행
                            task.run();

                            return null;
                        });

                futures.add(future);
            }

            // 모든 스레드가 준비될 때까지 대기
            assertTrue(
                    readyLatch.await(
                            10,
                            TimeUnit.SECONDS
                    ),
                    "스레드 준비 시간 초과"
            );

            // 모든 스레드에 시작 신호 전달
            startLatch.countDown();

            // 모든 요청의 완료 및 예외 확인
            for (Future<?> future : futures) {

                future.get(
                        30,
                        TimeUnit.SECONDS
                );
            }

        } finally {

            // 스레드 자원 정리
            executorService.shutdownNow();

            assertTrue(
                    executorService.awaitTermination(
                            10,
                            TimeUnit.SECONDS
                    ),
                    "스레드 종료 시간 초과"
            );
        }
    }

    @Test
    @DisplayName("여러 회원이 동시에 예약해도 회차 정원을 초과하지 않는다")
    void createReservation_concurrentCapacity() throws Exception {

        int threadCount = 10;
        int capacity = 5;
        int peopleCount = 1;

        // 성공 및 실패 횟수 관리
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 동시 실행할 작업 목록
        List<Runnable> tasks = new ArrayList<>();

        // 서로 다른 회원 10명 생성
        for (int i = 0; i < threadCount; i++) {

            User user = createUser();

            String username = user.getUsername();

            ReservationCreateRequest request =
                    new ReservationCreateRequest(
                            timeSlotId,
                            peopleCount
                    );

            // 각 회원의 예약 요청 등록
            tasks.add(() -> {

                try {
                    ReservationResponse response =
                            reservationService.createReservation(username, request);

                    // 예약 생성 성공 검증
                    assertNotNull(response.reservationId());

                    // 예약 상태 검증
                    assertEquals(
                            ReservationStatus.HOLD,
                            response.reservationStatus()
                    );

                    // 성공 횟수 증가
                    successCount.incrementAndGet();

                } catch (ReservationException e) {

                    // 정원 초과에 의한 실패인지 검증
                    assertEquals(
                            ReservationErrorCode.CAPACITY_EXCEEDED,
                            e.getErrorCode()
                    );

                    // 실패 횟수 증가
                    failureCount.incrementAndGet();
                }
            });
        }

        // 10개의 예약 요청을 동시에 실행
        executeConcurrently(tasks);

        // 예약 성공 횟수 검증
        assertEquals(capacity, successCount.get());

        // 예약 실패 횟수 검증
        assertEquals(threadCount - capacity, failureCount.get());

        // 전체 요청 처리 횟수 검증
        assertEquals(threadCount, successCount.get() + failureCount.get());

        // 실제 DB에서 회차 정보 재조회
        TimeSlot result = timeSlotRepository.findById(timeSlotId).orElseThrow();

        // 실제 DB에 반영된 예약 인원 검증
        assertEquals(capacity, result.getReservedCount());

        // 전체 정원 초과 여부 검증
        assertTrue(result.getReservedCount() <= result.getCapacity());

        // 실제 DB 예약 목록 조회
        List<Reservation> reservations = findTestReservations();

        // 실제 생성된 예약 개수 검증
        assertEquals(capacity, reservations.size());

        // 모든 예약이 HOLD 상태인지 검증
        assertTrue(
                reservations.stream()
                        .allMatch(reservation -> reservation.getReservationStatus() == ReservationStatus.HOLD)
        );

        // 7. 실제 예약 인원 합계 계산
        int totalPeopleCount = reservations.stream()
                        .mapToInt(Reservation::getPeopleCount)
                        .sum();

        // 예약 인원 합계와 회차 예약 인원이 일치하는지 검증
        assertEquals(result.getReservedCount(), totalPeopleCount);
        assertEquals(capacity, totalPeopleCount);

        // 8. 테스트 결과 출력
        System.out.println("===== 예약 동시성 테스트 결과 =====");
        System.out.println("전체 요청: " + threadCount);
        System.out.println("예약 성공: " + successCount.get());
        System.out.println("예약 실패: " + failureCount.get());
        System.out.println("회차 정원: " + result.getCapacity());
        System.out.println("최종 예약 인원: " + result.getReservedCount());
        System.out.println("실제 DB 예약 개수: " + reservations.size());
        System.out.println("실제 예약 인원 합계: " + totalPeopleCount);
    }

    @Test
    @DisplayName("동일 회원이 동시에 예약해도 중복 예약이 발생하지 않는다")
    void createReservation_concurrentDuplicate() throws Exception {

        int threadCount = 10;
        int peopleCount = 1;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Runnable> tasks = new ArrayList<>();

        // 동일한 테스트 회원 1명 생성
        User user = createUser();

        String username = user.getUsername();

        // 동일 회차, 동일 예약 인원 요청
        ReservationCreateRequest request = new ReservationCreateRequest(timeSlotId, peopleCount);

        // 동일 회원의 예약 요청 10개 등록
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                try {
                    ReservationResponse response = reservationService.createReservation(username, request);

                    // 정상 예약 생성 검증
                    assertNotNull(response.reservationId());

                    assertEquals(ReservationStatus.HOLD, response.reservationStatus());

                    successCount.incrementAndGet();

                } catch (ReservationException e) {

                    // 중복 예약에 의한 실패인지 검증
                    assertEquals(
                            ReservationErrorCode.DUPLICATE_RESERVATION,
                            e.getErrorCode()
                    );

                    failureCount.incrementAndGet();
                }
            });
        }

        // 동일 회원의 예약 요청 10건 동시 실행
        executeConcurrently(tasks);

        // 예약 성공 횟수 검증
        assertEquals(1, successCount.get());

        // 중복 예약 실패 횟수 검증
        assertEquals(threadCount - 1, failureCount.get());

        // 전체 요청 처리 횟수 검증
        assertEquals(threadCount, successCount.get() + failureCount.get());

        // 실제 DB 회차 정보 조회
        TimeSlot result = timeSlotRepository
                .findById(timeSlotId)
                .orElseThrow();

        // 정원이 한 번만 차감되었는지 검증
        assertEquals(peopleCount, result.getReservedCount());

        // 실제 DB 예약 목록 조회
        List<Reservation> reservations = findTestReservations();

        // 예약이 하나만 생성되었는지 검증
        assertEquals(1, reservations.size());

        Reservation savedReservation = reservations.get(0);

        // 실제 예약 상태 검증
        assertEquals(ReservationStatus.HOLD, savedReservation.getReservationStatus());

        // 실제 예약 인원 검증
        assertEquals(peopleCount, savedReservation.getPeopleCount());

        // 회차 예약 인원과 실제 예약 인원의 정합성 검증
        assertEquals(result.getReservedCount(), savedReservation.getPeopleCount());

        // 실제 DB에 해당 회원의 활성 예약이 존재하는지 검증
        assertTrue(
                reservationRepository.existsActiveReservation(
                        user.getId(),
                        timeSlotId,
                        List.of(
                                ReservationStatus.HOLD,
                                ReservationStatus.CONFIRMED,
                                ReservationStatus.USED
                        )
                )
        );

        // 테스트 결과 출력
        System.out.println("===== 중복 예약 동시성 테스트 결과 =====");
        System.out.println("전체 요청: " + threadCount);
        System.out.println("요청 회원: " + username);
        System.out.println("예약 성공: " + successCount.get());
        System.out.println("예약 실패: " + failureCount.get());
        System.out.println("회차 정원: " + result.getCapacity());
        System.out.println("최종 예약 인원: " + result.getReservedCount());
        System.out.println("실제 DB 예약 개수: " + reservations.size());
        System.out.println("실제 예약 상태: " + savedReservation.getReservationStatus());
    }

    @Test
    @DisplayName("예약 확정과 만료 처리가 동시에 발생해도 데이터 정합성이 유지된다")
    void confirmAndExpire_concurrent() throws Exception {

        int peopleCount = 2;
        User user = createUser();
        String username = user.getUsername();

        // 만료 시간이 지난 HOLD 예약 생성
        Reservation reservation = createExpiredHoldReservation(user, peopleCount);

        Long reservationId = reservation.getReservationId();

        // 초기 예약 상태 검증
        assertEquals(ReservationStatus.HOLD, reservation.getReservationStatus());

        // 초기 회차 예약 인원 검증
        TimeSlot beforeTimeSlot = timeSlotRepository.findById(timeSlotId).orElseThrow();

        assertEquals(peopleCount, beforeTimeSlot.getReservedCount());

        // 요청 결과 관리
        AtomicInteger confirmExpiredCount = new AtomicInteger(0);
        AtomicInteger confirmRejectedCount = new AtomicInteger(0);
        AtomicInteger expirationCompletedCount = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();

        // Thread A: 예약 확정 요청
        tasks.add(() -> {
            try {
                ReservationResponse response = reservationService.confirmReservation(username, reservationId);

                // 이미 만료 시간이 지났으므로
                // CONFIRMED가 아닌 EXPIRED 상태 반환
                assertEquals(ReservationStatus.EXPIRED, response.reservationStatus());

                assertNull(response.qrToken());

                confirmExpiredCount.incrementAndGet();

            } catch (ReservationException e) {

                // 만료 서비스가 먼저 실행된 경우
                // 이미 EXPIRED 상태이므로 확정 불가
                assertEquals(ReservationErrorCode.INVALID_RESERVATION_STATUS, e.getErrorCode());

                confirmRejectedCount.incrementAndGet();
            }
        });

        // Thread B: 예약 만료 요청
        tasks.add(() -> {
            reservationExpirationService.expireReservation(reservationId);

            expirationCompletedCount.incrementAndGet();
        });

        // 예약 확정과 만료 요청 동시 실행
        executeConcurrently(tasks);

        // 예약 확정 요청이 예상한 두 결과 중 하나로 처리되었는지 검증
        assertEquals(1, confirmExpiredCount.get() + confirmRejectedCount.get());

        // 만료 서비스 요청 정상 종료 검증
        assertEquals(1, expirationCompletedCount.get());

        // 실제 DB 예약 정보 재조회
        Reservation result = reservationRepository.findById(reservationId).orElseThrow();

        // 최종 예약 상태는 반드시 EXPIRED
        assertEquals(ReservationStatus.EXPIRED, result.getReservationStatus());

        // 만료된 예약에 QR 토큰이 발급되면 안 됨
        assertNull(result.getQrToken());

        // 실제 DB 회차 정보 재조회
        TimeSlot resultTimeSlot = timeSlotRepository.findById(timeSlotId).orElseThrow();

        // 기존 예약 인원 2명이 정상적으로 복구되었는지 검증
        assertEquals(0, resultTimeSlot.getReservedCount());

        // 예약 데이터 개수 검증
        List<Reservation> reservations = findTestReservations();

        assertEquals(1, reservations.size());

        // 활성 예약이 남아 있지 않은지 검증
        assertFalse(
                reservationRepository.existsActiveReservation(
                        user.getId(),
                        timeSlotId,
                        List.of(
                                ReservationStatus.HOLD,
                                ReservationStatus.CONFIRMED,
                                ReservationStatus.USED
                        )
                )
        );

        // 테스트 결과 출력
        System.out.println("===== 예약 확정 및 만료 동시성 테스트 결과 =====");
        System.out.println("전체 요청: 2");
        System.out.println("확정 요청 중 만료 처리 반환: " + confirmExpiredCount.get());
        System.out.println("확정 요청 거절: " + confirmRejectedCount.get());
        System.out.println("만료 서비스 정상 종료: " + expirationCompletedCount.get());
        System.out.println("기존 예약 인원: " + peopleCount);
        System.out.println("최종 예약 인원: " + resultTimeSlot.getReservedCount());
        System.out.println("최종 예약 상태: " + result.getReservationStatus());
        System.out.println("QR 토큰: " + result.getQrToken());
        System.out.println("실제 DB 예약 개수: " + reservations.size());
    }

    @Test
    @DisplayName("동일 예약을 동시에 만료 처리해도 정원이 중복 복구되지 않는다")
    void expireReservation_concurrent() throws Exception {

        int threadCount = 10;
        int expiredPeopleCount = 2;
        int confirmedPeopleCount = 3;

        // 서로 다른 테스트 회원 2명 생성
        User expiredUser = createUser();
        User confirmedUser = createUser();

        // 정상 예약 생성
        ReservationCreateRequest request = new ReservationCreateRequest(timeSlotId, confirmedPeopleCount);

        ReservationResponse createdReservation =
                reservationService.createReservation(confirmedUser.getUsername(), request);

        // 정상 예약 확정
        ReservationResponse confirmedReservation =
                reservationService.confirmReservation(confirmedUser.getUsername(), createdReservation.reservationId());

        Long confirmedReservationId = confirmedReservation.reservationId();

        // 정상 예약 상태 및 QR 토큰 검증
        assertEquals(ReservationStatus.CONFIRMED, confirmedReservation.reservationStatus());

        assertNotNull(confirmedReservation.qrToken());

        // 만료 시간이 지난 HOLD 예약 생성
        Reservation expiredReservation = createExpiredHoldReservation(expiredUser, expiredPeopleCount);

        Long expiredReservationId = expiredReservation.getReservationId();

        // 초기 만료 대상 예약 상태 검증
        assertEquals(ReservationStatus.HOLD, expiredReservation.getReservationStatus()
        );

        // 초기 회차 예약 인원 검증
        TimeSlot beforeTimeSlot = timeSlotRepository.findById(timeSlotId).orElseThrow();

        int beforeReservedCount = beforeTimeSlot.getReservedCount();

        // 정상 예약 3명 + 만료 대상 예약 2명
        assertEquals(expiredPeopleCount + confirmedPeopleCount, beforeReservedCount);

        assertEquals(5, beforeReservedCount);

        // 정상 종료 횟수 관리
        AtomicInteger completedCount = new AtomicInteger(0);

        List<Runnable> tasks = new ArrayList<>();

        // 동일 예약에 대한 만료 요청 10개 등록
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                reservationExpirationService.expireReservation(expiredReservationId);

                completedCount.incrementAndGet();
            });
        }

        // 동일 예약의 만료 요청 10건 동시 실행
        executeConcurrently(tasks);

        // 모든 만료 요청이 예외 없이 종료되었는지 검증
        assertEquals(threadCount, completedCount.get());

        // 실제 DB 만료 대상 예약 재조회
        Reservation expiredResult = reservationRepository
                .findById(expiredReservationId)
                .orElseThrow();

        // 만료 대상 예약 상태 검증
        assertEquals(ReservationStatus.EXPIRED, expiredResult.getReservationStatus());

        assertNull(expiredResult.getQrToken());

        // 실제 DB 정상 확정 예약 재조회
        Reservation confirmedResult = reservationRepository
                .findById(confirmedReservationId)
                .orElseThrow();

        // 정상 예약은 만료 처리의 영향을 받으면 안 됨
        assertEquals(ReservationStatus.CONFIRMED, confirmedResult.getReservationStatus());

        // 정상 예약 인원 유지 확인
        assertEquals(confirmedPeopleCount, confirmedResult.getPeopleCount());

        // 기존 QR 토큰 유지 확인
        assertEquals(confirmedReservation.qrToken(), confirmedResult.getQrToken());

        // 실제 DB 회차 정보 재조회
        TimeSlot resultTimeSlot = timeSlotRepository.findById(timeSlotId).orElseThrow();

        int afterReservedCount = resultTimeSlot.getReservedCount();

        // 만료 대상 예약 인원만 복구되어야 함
        assertEquals(beforeReservedCount - expiredPeopleCount, afterReservedCount);

        // 정상 예약 인원 3명이 그대로 유지되어야 함
        assertEquals(confirmedPeopleCount, afterReservedCount);

        // 실제 복구된 인원 검증
        int restoredCount = beforeReservedCount - afterReservedCount;

        assertEquals(expiredPeopleCount, restoredCount);

        // 실제 DB 전체 예약 목록 조회
        List<Reservation> reservations = findTestReservations();

        // 예약 데이터는 삭제되지 않고 모두 유지
        assertEquals(2, reservations.size());

        // 상태별 예약 개수 검증
        long expiredCount = reservations.stream()
                .filter(reservation ->
                        reservation.getReservationStatus() == ReservationStatus.EXPIRED
                )
                .count();

        long confirmedCount = reservations.stream()
                .filter(reservation ->
                        reservation.getReservationStatus() == ReservationStatus.CONFIRMED
                )
                .count();

        assertEquals(1, expiredCount);
        assertEquals(1, confirmedCount);

        // 활성 예약 인원 합계 검증
        int activePeopleCount = reservations.stream()
                .filter(reservation ->
                        List.of(
                                ReservationStatus.HOLD,
                                ReservationStatus.CONFIRMED,
                                ReservationStatus.USED
                        ).contains(
                                reservation.getReservationStatus()
                        )
                )
                .mapToInt(Reservation::getPeopleCount)
                .sum();

        // 실제 활성 예약 인원과 회차 예약 인원이 일치해야 함
        assertEquals(activePeopleCount, afterReservedCount);

        // 테스트 결과 출력
        System.out.println("===== 중복 만료 동시성 테스트 결과 =====");
        System.out.println("전체 만료 요청: " + threadCount);
        System.out.println("정상 종료 요청: " + completedCount.get());
        System.out.println("회차 정원: " + resultTimeSlot.getCapacity());
        System.out.println("기존 예약 인원: " + beforeReservedCount);
        System.out.println("만료 대상 예약 인원: " + expiredPeopleCount);
        System.out.println("실제 복구 인원: " + restoredCount);
        System.out.println("최종 예약 인원: " + afterReservedCount);
        System.out.println("만료 대상 예약 상태: " + expiredResult.getReservationStatus());
        System.out.println("정상 예약 상태: " + confirmedResult.getReservationStatus());
        System.out.println("실제 DB 예약 개수: " + reservations.size());
        System.out.println("EXPIRED 예약 개수: " + expiredCount);
        System.out.println("CONFIRMED 예약 개수: " + confirmedCount);
        System.out.println("활성 예약 인원 합계: " + activePeopleCount);
    }
}