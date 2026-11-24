package com.popq.platform.order.dto;

import com.popq.platform.order.entity.Order;

public record RecentDeliveryResponse(
        String receiverName,
        String receiverPhone,
        String deliveryAddress,
        String deliveryAddressDetail,
        String deliveryMemo
) {

    public static RecentDeliveryResponse from(Order order) {
        return new RecentDeliveryResponse(
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getDeliveryAddress(),
                order.getDeliveryAddressDetail(),
                order.getDeliveryMemo()
        );
    }
}