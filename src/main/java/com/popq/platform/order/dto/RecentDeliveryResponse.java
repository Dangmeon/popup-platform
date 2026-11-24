package com.popq.platform.order.dto;

import com.popq.platform.order.entity.Order;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecentDeliveryResponse {

    private String receiverName;
    private String receiverPhone;
    private String deliveryAddress;
    private String deliveryAddressDetail;
    private String deliveryMemo;

    public static RecentDeliveryResponse from(Order order) {
        return RecentDeliveryResponse.builder()
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .deliveryAddress(order.getDeliveryAddress())
                .deliveryAddressDetail(order.getDeliveryAddressDetail())
                .deliveryMemo(order.getDeliveryMemo())
                .build();
    }

}
