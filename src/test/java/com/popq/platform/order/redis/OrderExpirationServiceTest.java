package com.popq.platform.order.redis;

import com.popq.platform.auth.entity.User;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.product.entity.Product;
import com.popq.platform.product.repository.ProductRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderExpirationService orderExpirationService;


    // 공통 테스트 데이터
    private static final Long ORDER_ID = 1L;
    private static final Long PRD_ID = 2L;
    private static final int UNIT_PRICE = 10000;

    // 공통 테스트 데이터 생성 메서드

    // 실제 주문 엔티티 생성
    private Order createOrder(LocalDateTime expiresAt, OrderStatus orderStatus) {

        User user = mock(User.class);

        Order order = Order.createOrder(
                user,
                50000,
                expiresAt,
                "테스트 회원",
                "01012345678",
                "테스트 주소",
                "101호",
                "문 앞에 놓아주세요",
                null
        );

        // 테스트에 필요한 주문 상태 설정
        switch (orderStatus) {

            case PAID -> order.completePayment();

            case CANCELLED -> order.cancel();

            case EXPIRED -> order.expire();

            default -> {
                // PENDING 상태 유지
            }
        }

        return order;
    }

    // 주문 상품 추가
    private void addOrderItem(Order order, Long prdId, int quantity) {

        Product product = mock(Product.class);

        when(product.getPrdId()).thenReturn(prdId);

        OrderItem orderItem = OrderItem.createOrderItem(
                product,
                quantity,
                UNIT_PRICE,
                UNIT_PRICE * quantity
        );

        order.addOrderItem(orderItem);
    }

    // 주문 비관적 락 조회 설정
    private void mockOrderLookup(Order order) {

        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
    }

    @Test
    @DisplayName("존재하지 않는 주문은 만료 처리할 수 없다")
    void expireOrder_orderNotFound() {

        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        OrderException exception = assertThrows(
                        OrderException.class,
                        () -> orderExpirationService.expireOrder(ORDER_ID)
        );

        assertEquals(OrderErrorCode.ORDER_NOT_FOUND, exception.getErrorCode());

        verify(orderRepository).findByIdForUpdate(ORDER_ID);

        // 존재하지 않는 주문의 재고 복구 방지
        verifyNoInteractions(productRepository);
    }

    @ParameterizedTest
    @EnumSource(
            value = OrderStatus.class,
            names = {
                    "PAID",
                    "CANCELLED",
                    "EXPIRED"
            }
    )
    @DisplayName("PENDING 상태가 아닌 주문은 만료 처리하지 않는다")
    void expireOrder_notPending(OrderStatus orderStatus) {

        Order order = createOrder(LocalDateTime.now().minusMinutes(5), orderStatus);

        mockOrderLookup(order);

        assertDoesNotThrow(() -> orderExpirationService.expireOrder(ORDER_ID));

        // 기존 주문 상태 유지
        assertEquals(orderStatus, order.getOrderStatus());

        verify(orderRepository).findByIdForUpdate(ORDER_ID);

        // 재고 중복 복구 방지
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("만료 시간이 지나지 않은 주문은 만료 처리하지 않는다")
    void expireOrder_notExpiredYet() {

        Order order = createOrder(LocalDateTime.now().plusMinutes(5), OrderStatus.PENDING);

        mockOrderLookup(order);

        orderExpirationService.expireOrder(ORDER_ID);

        // 결제 대기 상태 유지
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        verify(orderRepository).findByIdForUpdate(ORDER_ID);

        // 만료 시간 이전에는 재고 복구 금지
        verifyNoInteractions(productRepository);
    }

    @Test
    @DisplayName("만료 시간이 지난 주문은 모든 상품의 재고를 복구하고 EXPIRED로 변경한다")
    void expireOrder_success() {

        Long secondPrdId = 3L;

        Order order = createOrder(LocalDateTime.now().minusMinutes(5), OrderStatus.PENDING);

        // 첫 번째 상품 2개
        addOrderItem(order, PRD_ID, 2);

        // 두 번째 상품 3개
        addOrderItem(order, secondPrdId, 3);

        mockOrderLookup(order);

        when(productRepository.increaseStock(PRD_ID, 2)).thenReturn(1);
        when(productRepository.increaseStock(secondPrdId, 3)).thenReturn(1);

        // 초기 주문 상태 검증
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        orderExpirationService.expireOrder(ORDER_ID);

        // 주문 상태 변경 검증
        assertEquals(OrderStatus.EXPIRED, order.getOrderStatus());

        // 재고 복구 순서 검증
        InOrder inOrder = inOrder(orderRepository, productRepository);

        // 주문 비관적 락 조회
        inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);

        // 첫 번째 상품 재고 복구
        inOrder.verify(productRepository).increaseStock(PRD_ID, 2);

        // 두 번째 상품 재고 복구
        inOrder.verify(productRepository).increaseStock(secondPrdId, 3);

        // 추가 재고 복구 호출 방지
        verifyNoMoreInteractions(productRepository);

        // Dirty Checking 방식이므로
        // 별도의 주문 저장 호출 없음
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("재고 복구에 실패하면 주문 만료 처리가 완료되지 않는다")
    void expireOrder_stockRestoreFailed() {

        Order order = createOrder(LocalDateTime.now().minusMinutes(5), OrderStatus.PENDING);

        addOrderItem(order, PRD_ID, 2);

        mockOrderLookup(order);

        // 재고 복구 UPDATE 실패
        when(productRepository.increaseStock(PRD_ID, 2)).thenReturn(0);

        OrderException exception = assertThrows(
                OrderException.class,
                () -> orderExpirationService.expireOrder(ORDER_ID)
        );

        // 예외 메시지 검증
        assertEquals(OrderErrorCode.STOCK_RESTORE_FAILED, exception.getErrorCode());

        // 주문 상태가 EXPIRED로 변경되지 않아야 함
        assertEquals(OrderStatus.PENDING, order.getOrderStatus());

        // 재고 복구 호출 검증
        verify(productRepository).increaseStock(PRD_ID, 2);

        // 별도의 주문 저장 호출 없음
        verify(orderRepository, never()).save(any(Order.class));

        verifyNoMoreInteractions(productRepository);
    }
}