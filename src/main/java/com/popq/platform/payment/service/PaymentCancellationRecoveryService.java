package com.popq.platform.payment.service;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.payment.dto.TossPaymentResponse;
import com.popq.platform.payment.entity.Payment;
import com.popq.platform.payment.entity.PaymentOperation;
import com.popq.platform.payment.entity.PaymentOperationStatus;
import com.popq.platform.payment.entity.PaymentOperationType;
import com.popq.platform.payment.entity.PaymentStatus;
import com.popq.platform.payment.exception.PaymentErrorCode;
import com.popq.platform.payment.exception.PaymentException;
import com.popq.platform.payment.repository.PaymentOperationRepository;
import com.popq.platform.payment.repository.PaymentRepository;
import com.popq.platform.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCancellationRecoveryService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;

    @Transactional
    public void recoverCancelPayment(Long orderId, Long operationId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        PaymentOperation operation = paymentOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_NOT_FOUND));

        if (!operation.getOrderId().equals(orderId) || operation.getOperationType() != PaymentOperationType.CANCEL) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
        }

        if (operation.getOperationStatus() == PaymentOperationStatus.COMPLETED
                || operation.getOperationStatus() == PaymentOperationStatus.FAILED) {
            return;
        }

        Payment payment = paymentRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        TossPaymentResponse tossPayment = paymentService.getTossPayment(operation.getPaymentKey());

        validateRecoveryPayment(order, payment, operation, tossPayment);

        // 외부 결제가 전액 취소되지 않았다면 복구 대상으로 유지
        if (!tossPayment.isCanceled() || tossPayment.balanceAmount() != 0) {
            operation.requireRecovery("Toss 결제 상태: " + tossPayment.status());
            return;
        }

        // 이미 내부 취소까지 완료된 경우
        if (order.getOrderStatus() == OrderStatus.CANCELLED
                && payment.getPaymentStatus() == PaymentStatus.CANCELLED) {
            operation.complete();
            return;
        }

        if (order.getOrderStatus() != OrderStatus.PAID
                || payment.getPaymentStatus() != PaymentStatus.PAID) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
        }

        payment.cancel();

        restoreStock(order);

        order.cancel();

        operation.complete();
    }

    private void validateRecoveryPayment(Order order, Payment payment, PaymentOperation operation, TossPaymentResponse tossPayment) {

        if (!order.getTossOrderId().equals(tossPayment.orderId())
                || !operation.getTossOrderId().equals(tossPayment.orderId())
                || !operation.getPaymentKey().equals(tossPayment.paymentKey())
                || !payment.getPaymentKey().equals(tossPayment.paymentKey())
                || operation.getAmount() != tossPayment.totalAmount()
                || payment.getAmount() != operation.getAmount()
                || order.getTotalPrice() != operation.getAmount()) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
        }
    }

    private void restoreStock(Order order) {

        for (OrderItem orderItem : order.getOrderItems()) {
            int updatedRows = productRepository.increaseStock(orderItem.getProduct().getPrdId(), orderItem.getQuantity());

            if (updatedRows == 0) {
                throw new OrderException(OrderErrorCode.STOCK_RESTORE_FAILED);
            }
        }
    }
}