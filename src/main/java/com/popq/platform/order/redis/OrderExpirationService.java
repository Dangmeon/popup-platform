package com.popq.platform.order.redis;

import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderItem;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.order.exception.OrderErrorCode;
import com.popq.platform.order.exception.OrderException;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.product.repository.ProductRepository;
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
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND));

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
                throw new OrderException(OrderErrorCode.STOCK_RESTORE_FAILED);
            }
        }

        order.expire();
    }

}
