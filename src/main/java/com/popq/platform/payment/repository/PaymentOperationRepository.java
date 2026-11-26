package com.popq.platform.payment.repository;

import com.popq.platform.payment.entity.PaymentOperation;
import com.popq.platform.payment.entity.PaymentOperationStatus;
import com.popq.platform.payment.entity.PaymentOperationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {

    // 기존 결제 처리 이력 조회
    Optional<PaymentOperation> findByIdempotencyKey(String idempotencyKey);

    // 결제 처리 이력 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM PaymentOperation p
        WHERE p.operationId = :operationId
    """)
    Optional<PaymentOperation> findByIdForUpdate(@Param("operationId") Long operationId);

    // 스케줄러 복구 대상 조회
    @Query("""
        SELECT p.operationId
        FROM PaymentOperation p
        WHERE p.operationStatus = :status
            AND p.updatedAt <= :before
        ORDER BY p.updatedAt ASC, p.operationId ASC
    """)
    List<Long> findRecoveryTargetIds(
            @Param("status") PaymentOperationStatus status,
            @Param("before") LocalDateTime before,
            Pageable pageable
    );

    // 미완료 결제 처리 이력 존재 여부
    boolean existsByOrderIdAndOperationTypeAndOperationStatus(
            Long orderId,
            PaymentOperationType operationType,
            PaymentOperationStatus status
    );

    // 주문별 결제 처리 이력 조회
    List<PaymentOperation> findAllByOrderId(Long orderId);
}