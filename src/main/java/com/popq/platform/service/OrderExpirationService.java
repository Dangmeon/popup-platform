package com.popq.platform.service;

import com.popq.platform.entity.*;
import com.popq.platform.repository.OrderRepository;
import com.popq.platform.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrderExpirationService {

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void expireOrder(Long orderId) {

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문이 없습니다."));

        if(order.getOrderStatus() != OrderStatus.PENDING){
            return;
        }

        if(order.getExpiresAt().isAfter(LocalDateTime.now())){
            return;
        }

        for(OrderItem orderItem : order.getOrderItems()){

            int updatedRows = productRepository.increaseStock(
                    orderItem.getProduct().getPrdId(),
                    orderItem.getQuantity()
            );

            if(updatedRows == 0){
                throw new IllegalArgumentException("주문 만료 중 재고 복구에 실패했습니다.");
            }
        }

        order.expire();
    }

}
