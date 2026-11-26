package com.popq.platform.payment.service;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.payment.dto.TossPaymentResponse;
import com.popq.platform.payment.entity.PaymentOperation;
import com.popq.platform.payment.entity.PaymentOperationStatus;
import com.popq.platform.payment.entity.PaymentOperationType;
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
public class PaymentCompensationService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentOperationRepository paymentOperationRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;

    // 결제 승인 복구 실패에 따른 보상 취소
    @Transactional
    public void compensatePayment(Long orderId, Long operationId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

        PaymentOperation operation = paymentOperationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_NOT_FOUND));

        if (!operation.getOrderId().equals(orderId) || operation.getOperationType() != PaymentOperationType.CONFIRM) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_OPERATION_MISMATCH);
        }

        if (operation.getOperationStatus() != PaymentOperationStatus.COMPENSATION_REQUIRED) {
            return;
        }

        if (order.getOrderStatus() == OrderStatus.PAID || paymentRepository.findByOrder_OrderId(orderId).isPresent()) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
        }

        if (order.getOrderStatus() != OrderStatus.PENDING
                && order.getOrderStatus() != OrderStatus.CANCELLED
                && order.getOrderStatus() != OrderStatus.EXPIRED) {
            throw new OrderException(OrderErrorCode.INVALID_ORDER_STATUS);
        }

        TossPaymentResponse tossPayment = paymentService.getTossPayment(operation.getPaymentKey());

        validateTossPayment(order, operation, tossPayment);

        // 아직 외부 결제가 완료 상태라면 보상 취소 요청
        if (tossPayment.isDone()) {

            if (tossPayment.balanceAmount() != operation.getAmount()) {
                throw new PaymentException(PaymentErrorCode.INVALID_TOSS_PAYMENT_RESPONSE);
            }

            paymentService.compensateTossPayment(operation.getPaymentKey());

            tossPayment = paymentService.getTossPayment(operation.getPaymentKey());

            validateTossPayment(order, operation, tossPayment);
        }

        // 외부 결제 전액 취소 확인
        if (!tossPayment.isCanceled() || tossPayment.balanceAmount() != 0) {
            return;
        }

        // 아직 재고가 복구되지 않은 주문만 취소 처리
        if (order.getOrderStatus() == OrderStatus.PENDING) {
            restoreStock(order);
            order.cancel();
        }

        operation.fail("내부 결제 복구 실패로 인한 보상 취소 완료");
    }

    private void validateTossPayment(Order order, PaymentOperation operation, TossPaymentResponse tossPayment) {

        if (!operation.getPaymentKey().equals(tossPayment.paymentKey())
                || !operation.getTossOrderId().equals(tossPayment.orderId())
                || !order.getTossOrderId().equals(tossPayment.orderId())
                || operation.getAmount() != tossPayment.totalAmount()
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