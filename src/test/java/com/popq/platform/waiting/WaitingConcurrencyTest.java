package com.popq.platform.waiting;

import com.popq.platform.auth.entity.Role;
import com.popq.platform.auth.entity.User;
import com.popq.platform.auth.repository.UserRepository;
import com.popq.platform.popup.entity.PopupStore;
import com.popq.platform.popup.repository.PopupStoreRepository;
import com.popq.platform.waiting.dto.WaitingRegisterRequest;
import com.popq.platform.waiting.entity.WaitingQueue;
import com.popq.platform.waiting.repository.WaitingQueueRepository;
import com.popq.platform.waiting.service.WaitingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WaitingConcurrencyTest {

    @Autowired
    private WaitingService waitingService;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private PopupStoreRepository popupStoreRepository;

    @Autowired
    private UserRepository userRepository;

    private PopupStore testPopup;
    private List<User> testUsers;


    @BeforeEach
    void setUp() {
        testPopup = popupStoreRepository.save(PopupStore.builder()
                .title("JUnit 동시성 검증 전용 팝업")
                .location("서울 성수동")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(5))
                .category("GOODS")
                .opStatus("OPEN")
                .build());

        testUsers = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            String phone = String.format("010-9696-%04d", i);
            User user = User.builder()
                    .username("junit_conc_user_" + i + "@test.com")
                    .password("encodedPw")
                    .name("동시성유저" + i)
                    .phone(phone)
                    .role(Role.USER)
                    .build();
            testUsers.add(user);
        }
        testUsers = userRepository.saveAll(testUsers);
    }

    @AfterEach
    void tearDown() {
        if (testPopup != null) {
            List<WaitingQueue> createdWaitings = waitingQueueRepository.findAll().stream()
                    .filter(w -> w.getPopupStore().getPopId().equals(testPopup.getPopId()))
                    .toList();
            waitingQueueRepository.deleteAllInBatch(createdWaitings);
            popupStoreRepository.delete(testPopup);
        }
        if (testUsers != null && !testUsers.isEmpty()) {
            userRepository.deleteAllInBatch(testUsers);
        }
    }
    @Test
    @DisplayName("동시성 테스트 1: 서로 다른 회원 100명이 동시에 대기 등록 시 순번 1~100이 중복/누락 없이 발급된다")
    void registerWaiting_100ConcurrentUsers_NoDuplicateQueueNumber() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(16);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final User user = testUsers.get(i);
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    WaitingRegisterRequest request = new WaitingRegisterRequest(
                            testPopup.getPopId(), null, user.getPhone(), 2
                    );
                    waitingService.registerWaiting(user.getUsername(), request);
                    successCount.incrementAndGet();
                } catch (Throwable t) {
                    failCount.incrementAndGet();
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdownNow();

        if (!errors.isEmpty()) {
            errors.get(0).printStackTrace();
        }
        assertThat(completed).as("30초 내에 100건 처리가 완료되어야 합니다").isTrue();

        List<WaitingQueue> savedList = waitingQueueRepository.findAll().stream()
                .filter(w -> w.getPopupStore().getPopId().equals(testPopup.getPopId()))
                .toList();

        Set<Integer> uniqueQueueNumbers = savedList.stream()
                .map(WaitingQueue::getQueueNumber)
                .collect(Collectors.toSet());

        Set<Integer> expectedNumbers = IntStream.rangeClosed(1, 100)
                .boxed()
                .collect(Collectors.toSet());

        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(savedList).hasSize(100);
        assertThat(uniqueQueueNumbers).containsExactlyInAnyOrderElementsOf(expectedNumbers);
    }

    @Test
    @DisplayName("동시성 테스트 2: 동일한 전화번호로 10개 스레드가 동시에 대기 등록 시 1건만 성공하고 9건은 실패한다")
    void registerWaiting_SamePhoneConcurrent_OnlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        String duplicatePhone = "010-8888-9999";
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final User user = testUsers.get(i);
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    WaitingRegisterRequest request = new WaitingRegisterRequest(
                            testPopup.getPopId(), null, duplicatePhone, 2
                    );
                    waitingService.registerWaiting(user.getUsername(), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executorService.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
        assertThat(waitingQueueRepository.findAll()).hasSize(1);
    }
}