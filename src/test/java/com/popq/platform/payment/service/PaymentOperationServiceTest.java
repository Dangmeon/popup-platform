package com.popq.platform.payment.service;

import com.popq.platform.payment.entity.PaymentOperation;
import com.popq.platform.payment.entity.PaymentOperationStatus;
import com.popq.platform.payment.entity.PaymentOperationType;
import com.popq.platform.payment.exception.PaymentErrorCode;
import com.popq.platform.payment.exception.PaymentException;
import com.popq.platform.payment.repository.PaymentOperationRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentOperationServiceTest {

    @Mock
    private PaymentOperationRepository paymentOperationRepository;

    @InjectMocks
    private PaymentOperationService paymentOperationService;


    // 공통 테스트 데이터

    private static final Long ORDER_ID = 1L;
    private static final Long OPERATION_ID = 2L;

    private static final String TOSS_ORDER_ID = "test-toss-order-id";
    private static final String PAYMENT_KEY = "test-payment-key";

    private static final int AMOUNT = 30000;

    private static final String CONFIRM_IDEMPOTENCY_KEY =
            "confirm-" + TOSS_ORDER_ID;

    private static final String CANCEL_IDEMPOTENCY_KEY =
            "cancel-" + PAYMENT_KEY;


    // 공통 테스트 메서드

    // 결제 작업 유형에 따른 멱등키 생성
    private String getIdempotencyKey(PaymentOperationType type) {

        return type == PaymentOperationType.CONFIRM
                ? CONFIRM_IDEMPOTENCY_KEY
                : CANCEL_IDEMPOTENCY_KEY;
    }

    // 실제 결제 처리 이력 엔티티 생성
    private PaymentOperation createOperation(
            PaymentOperationType type,
            PaymentOperationStatus status
    ) {

        PaymentOperation operation = PaymentOperation.create(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                getIdempotencyKey(type),
                type
        );

        ReflectionTestUtils.setField(
                operation, "operationId", OPERATION_ID
        );

        switch (status) {

            case COMPLETED -> operation.complete();

            case FAILED -> operation.fail();

            default -> {
                // PENDING 상태 유지
            }
        }

        return operation;
    }

    // 기본 결제 승인 처리 이력 생성
    private PaymentOperation createPendingOperation() {

        return createOperation(
                PaymentOperationType.CONFIRM,
                PaymentOperationStatus.PENDING
        );
    }

    // 결제 처리 이력 비관적 락 조회 설정
    private void mockOperationLookup(PaymentOperation operation) {

        when(paymentOperationRepository.findByIdForUpdate(OPERATION_ID))
                .thenReturn(Optional.of(operation));
    }

    // DB 처리 이력 ID 할당 동작 모방
    private void mockOperationSave() {

        when(paymentOperationRepository.saveAndFlush(any(PaymentOperation.class)))
                .thenAnswer(invocation -> {

                    PaymentOperation operation = invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            operation, "operationId", OPERATION_ID
                    );

                    return operation;
                });
    }

    // 신규 결제 처리 이력 생성 방지 검증
    private void verifyOperationNotCreated() {

        verify(paymentOperationRepository, never())
                .saveAndFlush(any(PaymentOperation.class));
    }

    @ParameterizedTest
    @EnumSource(PaymentOperationType.class)
    @DisplayName("신규 결제 승인 및 취소 처리 이력을 PENDING 상태로 생성한다")
    void createOperation_success(PaymentOperationType type) {

        String idempotencyKey = getIdempotencyKey(type);

        when(paymentOperationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());

        mockOperationSave();

        LocalDateTime before = LocalDateTime.now();

        Long operationId = paymentOperationService.createOperation(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                idempotencyKey,
                type
        );

        LocalDateTime after = LocalDateTime.now();

        assertEquals(OPERATION_ID, operationId);

        ArgumentCaptor<PaymentOperation> operationCaptor =
                ArgumentCaptor.forClass(PaymentOperation.class);

        InOrder inOrder = inOrder(paymentOperationRepository);

        // 기존 처리 이력 조회 이후 신규 저장
        inOrder.verify(paymentOperationRepository)
                .findByIdempotencyKey(idempotencyKey);

        inOrder.verify(paymentOperationRepository)
                .saveAndFlush(operationCaptor.capture());

        PaymentOperation savedOperation = operationCaptor.getValue();

        // 저장된 처리 이력 검증
        assertEquals(OPERATION_ID, savedOperation.getOperationId());
        assertEquals(ORDER_ID, savedOperation.getOrderId());
        assertEquals(TOSS_ORDER_ID, savedOperation.getTossOrderId());
        assertEquals(PAYMENT_KEY, savedOperation.getPaymentKey());
        assertEquals(AMOUNT, savedOperation.getAmount());
        assertEquals(idempotencyKey, savedOperation.getIdempotencyKey());

        // 초기 상태 검증
        assertEquals(type, savedOperation.getOperationType());

        assertEquals(
                PaymentOperationStatus.PENDING,
                savedOperation.getOperationStatus()
        );

        // 생성 및 수정 시간 검증
        assertNotNull(savedOperation.getCreatedAt());
        assertNotNull(savedOperation.getUpdatedAt());

        assertFalse(savedOperation.getCreatedAt().isBefore(before));
        assertFalse(savedOperation.getCreatedAt().isAfter(after));

        assertFalse(
                savedOperation.getUpdatedAt()
                        .isBefore(savedOperation.getCreatedAt())
        );

        assertFalse(savedOperation.getUpdatedAt().isAfter(after));

        verifyNoMoreInteractions(paymentOperationRepository);
    }

    @ParameterizedTest
    @EnumSource(PaymentOperationType.class)
    @DisplayName("동일 멱등키와 동일 결제 정보로 요청하면 기존 처리 이력 ID를 반환한다")
    void createOperation_existingOperation(PaymentOperationType type) {

        PaymentOperation existingOperation = createOperation(
                type, PaymentOperationStatus.PENDING
        );

        String idempotencyKey = getIdempotencyKey(type);

        LocalDateTime originalCreatedAt =
                existingOperation.getCreatedAt();

        LocalDateTime originalUpdatedAt =
                existingOperation.getUpdatedAt();

        when(paymentOperationRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(existingOperation));

        Long operationId = paymentOperationService.createOperation(
                ORDER_ID,
                TOSS_ORDER_ID,
                PAYMENT_KEY,
                AMOUNT,
                idempotencyKey,
                type
        );

        // 기존 처리 이력 ID 반환
        assertEquals(OPERATION_ID, operationId);

        // 기존 처리 이력 변경 방지
        assertEquals(
                PaymentOperationStatus.PENDING,
                existingOperation.getOperationStatus()
        );

        assertEquals(originalCreatedAt, existingOperation.getCreatedAt());
        assertEquals(originalUpdatedAt, existingOperation.getUpdatedAt());

        verify(paymentOperationRepository)
                .findByIdempotencyKey(idempotencyKey);

        verifyOperationNotCreated();

        verifyNoMoreInteractions(paymentOperationRepository);
    }

    private enum MismatchType {

        ORDER_ID,
        TOSS_ORDER_ID,
        PAYMENT_KEY,
        AMOUNT,
        OPERATION_TYPE
    }

    @ParameterizedTest
    @EnumSource(MismatchType.class)
    @DisplayName("동일 멱등키의 기존 처리 이력과 요청 정보가 다르면 예외가 발생한다")
    void createOperation_mismatch(MismatchType mismatchType) {

        PaymentOperation existingOperation = createPendingOperation();

        when(paymentOperationRepository.findByIdempotencyKey(
                CONFIRM_IDEMPOTENCY_KEY
        )).thenReturn(Optional.of(existingOperation));

        // 불일치 대상에 따라 요청 데이터 변경
        Long orderId = mismatchType == MismatchType.ORDER_ID
                ? 999L
                : ORDER_ID;

        String tossOrderId = mismatchType == MismatchType.TOSS_ORDER_ID
                ? "other-toss-order-id"
                : TOSS_ORDER_ID;

        String paymentKey = mismatchType == MismatchType.PAYMENT_KEY
                ? "other-payment-key"
                : PAYMENT_KEY;

        int amount = mismatchType == MismatchType.AMOUNT
                ? 10000
                : AMOUNT;

        PaymentOperationType type = mismatchType == MismatchType.OPERATION_TYPE
                ? PaymentOperationType.CANCEL
                : PaymentOperationType.CONFIRM;

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentOperationService.createOperation(
                        orderId,
                        tossOrderId,
                        paymentKey,
                        amount,
                        CONFIRM_IDEMPOTENCY_KEY,
                        type
                )
        );

        assertEquals(
                PaymentErrorCode.PAYMENT_OPERATION_MISMATCH,
                exception.getErrorCode()
        );

        // 기존 처리 이력 유지
        assertEquals(ORDER_ID, existingOperation.getOrderId());
        assertEquals(TOSS_ORDER_ID, existingOperation.getTossOrderId());
        assertEquals(PAYMENT_KEY, existingOperation.getPaymentKey());
        assertEquals(AMOUNT, existingOperation.getAmount());

        assertEquals(
                PaymentOperationType.CONFIRM,
                existingOperation.getOperationType()
        );

        assertEquals(
                PaymentOperationStatus.PENDING,
                existingOperation.getOperationStatus()
        );

        verifyOperationNotCreated();
    }

    @Test
    @DisplayName("PENDING 결제 처리 이력을 COMPLETED 상태로 변경한다")
    void completeOperation_success() {

        PaymentOperation operation = createPendingOperation();

        LocalDateTime originalCreatedAt = operation.getCreatedAt();

        mockOperationLookup(operation);

        LocalDateTime before = LocalDateTime.now();

        paymentOperationService.completeOperation(OPERATION_ID);

        LocalDateTime after = LocalDateTime.now();

        assertEquals(
                PaymentOperationStatus.COMPLETED,
                operation.getOperationStatus()
        );

        // 기존 생성 시간 유지
        assertEquals(originalCreatedAt, operation.getCreatedAt());

        // 수정 시간 갱신 검증
        assertFalse(operation.getUpdatedAt().isBefore(before));
        assertFalse(operation.getUpdatedAt().isAfter(after));

        verify(paymentOperationRepository)
                .findByIdForUpdate(OPERATION_ID);

        // Dirty Checking 방식이므로 별도 저장 요청 없음
        verifyNoMoreInteractions(paymentOperationRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentOperationStatus.class,
            names = {"COMPLETED", "FAILED"}
    )
    @DisplayName("이미 완료되거나 실패한 처리 이력은 변경하지 않는다")
    void completeOperation_skipFinalStatus(
            PaymentOperationStatus status
    ) {

        PaymentOperation operation = createOperation(
                PaymentOperationType.CONFIRM,
                status
        );

        LocalDateTime originalUpdatedAt = operation.getUpdatedAt();

        mockOperationLookup(operation);

        paymentOperationService.completeOperation(OPERATION_ID);

        // 기존 상태 및 수정 시간 유지
        assertEquals(status, operation.getOperationStatus());

        assertEquals(
                originalUpdatedAt,
                operation.getUpdatedAt()
        );

        verify(paymentOperationRepository)
                .findByIdForUpdate(OPERATION_ID);

        verifyNoMoreInteractions(paymentOperationRepository);
    }

    private enum OperationAction {

        COMPLETE,
        TOUCH
    }

    @ParameterizedTest
    @EnumSource(OperationAction.class)
    @DisplayName("존재하지 않는 결제 처리 이력은 변경할 수 없다")
    void updateOperation_notFound(OperationAction action) {

        when(paymentOperationRepository.findByIdForUpdate(OPERATION_ID))
                .thenReturn(Optional.empty());

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> {

                    switch (action) {

                        case COMPLETE ->
                                paymentOperationService.completeOperation(OPERATION_ID);

                        case TOUCH ->
                                paymentOperationService.touchOperation(OPERATION_ID);
                    }
                }
        );

        assertEquals(
                PaymentErrorCode.PAYMENT_OPERATION_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(paymentOperationRepository)
                .findByIdForUpdate(OPERATION_ID);

        verifyNoMoreInteractions(paymentOperationRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("미완료 결제 승인 처리 이력의 존재 여부를 반환한다")
    void hasUnresolvedConfirm(boolean exists) {

        when(paymentOperationRepository
                .existsByOrderIdAndOperationTypeAndOperationStatus(
                        ORDER_ID,
                        PaymentOperationType.CONFIRM,
                        PaymentOperationStatus.PENDING
                )).thenReturn(exists);

        boolean result =
                paymentOperationService.hasUnresolvedConfirm(ORDER_ID);

        assertEquals(exists, result);

        // CONFIRM 유형의 PENDING 상태만 조회하는지 검증
        verify(paymentOperationRepository)
                .existsByOrderIdAndOperationTypeAndOperationStatus(
                        ORDER_ID,
                        PaymentOperationType.CONFIRM,
                        PaymentOperationStatus.PENDING
                );

        verifyNoMoreInteractions(paymentOperationRepository);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("멱등키를 통해 기존 결제 처리 이력의 존재 여부를 확인한다")
    void existsOperation(boolean exists) {

        Optional<PaymentOperation> operation = exists
                ? Optional.of(createPendingOperation())
                : Optional.empty();

        when(paymentOperationRepository.findByIdempotencyKey(
                CONFIRM_IDEMPOTENCY_KEY
        )).thenReturn(operation);

        boolean result = paymentOperationService.existsOperation(
                CONFIRM_IDEMPOTENCY_KEY
        );

        assertEquals(exists, result);

        verify(paymentOperationRepository)
                .findByIdempotencyKey(CONFIRM_IDEMPOTENCY_KEY);

        verifyNoMoreInteractions(paymentOperationRepository);
    }

    @Test
    @DisplayName("복구 실패 시 PENDING 상태를 유지하고 수정 시간만 갱신한다")
    void touchOperation_success() {

        PaymentOperation operation = createPendingOperation();

        // 이전 복구 시도 시간을 과거로 설정
        LocalDateTime previousUpdatedAt =
                LocalDateTime.now().minusMinutes(5);

        ReflectionTestUtils.setField(
                operation, "updatedAt", previousUpdatedAt
        );

        LocalDateTime originalCreatedAt = operation.getCreatedAt();

        mockOperationLookup(operation);

        LocalDateTime before = LocalDateTime.now();

        paymentOperationService.touchOperation(OPERATION_ID);

        LocalDateTime after = LocalDateTime.now();

        // 상태는 변경하지 않음
        assertEquals(
                PaymentOperationStatus.PENDING,
                operation.getOperationStatus()
        );

        // 기존 생성 시간 유지
        assertEquals(originalCreatedAt, operation.getCreatedAt());

        // 마지막 복구 시도 시간 갱신
        assertTrue(
                operation.getUpdatedAt().isAfter(previousUpdatedAt)
        );

        assertFalse(operation.getUpdatedAt().isBefore(before));
        assertFalse(operation.getUpdatedAt().isAfter(after));

        verify(paymentOperationRepository)
                .findByIdForUpdate(OPERATION_ID);

        verifyNoMoreInteractions(paymentOperationRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = PaymentOperationStatus.class,
            names = {"COMPLETED", "FAILED"}
    )
    @DisplayName("완료되거나 실패한 처리 이력은 수정 시간을 갱신하지 않는다")
    void touchOperation_skipFinalStatus(
            PaymentOperationStatus status
    ) {

        PaymentOperation operation = createOperation(
                PaymentOperationType.CONFIRM,
                status
        );

        LocalDateTime originalUpdatedAt = operation.getUpdatedAt();

        mockOperationLookup(operation);

        paymentOperationService.touchOperation(OPERATION_ID);

        assertEquals(status, operation.getOperationStatus());

        assertEquals(
                originalUpdatedAt,
                operation.getUpdatedAt()
        );

        verify(paymentOperationRepository)
                .findByIdForUpdate(OPERATION_ID);

        verifyNoMoreInteractions(paymentOperationRepository);
    }
}