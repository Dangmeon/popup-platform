package com.popq.platform.payment.repository;

import com.popq.platform.payment.entity.PaymentOperation;
import com.popq.platform.payment.entity.PaymentOperationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {

    Optional<PaymentOperation> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM PaymentOperation p
        WHERE p.operationId = :operationId
    """)
    Optional<PaymentOperation> findByIdForUpdate(@Param("operationId") Long operationId);

    @Query("""
        SELECT p.operationId
        FROM PaymentOperation p
        WHERE p.operationStatus IN :statuses
            AND p.updatedAt <= :before
        ORDER BY p.updatedAt ASC
    """)
    List<Long> findRecoveryTargetIds(
            @Param("statuses") List<PaymentOperationStatus> statuses,
            @Param("before") LocalDateTime before
    );
}