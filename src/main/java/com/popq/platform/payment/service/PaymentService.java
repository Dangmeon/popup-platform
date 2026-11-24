package com.popq.platform.payment.service;

import com.popq.platform.payment.dto.PaymentRequest;
import com.popq.platform.payment.dto.PaymentResponse;
import com.popq.platform.order.entity.Order;
import com.popq.platform.order.entity.OrderStatus;
import com.popq.platform.payment.entity.Payment;
import com.popq.platform.exception.PaymentErrorCode;
import com.popq.platform.exception.PaymentException;
import com.popq.platform.redis.RedisExpirationKeyService;
import com.popq.platform.order.repository.OrderRepository;
import com.popq.platform.payment.repository.PaymentRepository;
import com.popq.platform.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RedisExpirationKeyService redisExpirationKeyService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final CouponService couponService;

    @Value("${toss.secret-key}")
    private String tossSecretKey;

    @Transactional
    public PaymentResponse confirmPayment(PaymentRequest request) {

        System.out.println("paymentKey = " + request.getPaymentKey());
        System.out.println("orderId = [" + request.getOrderId() + "]");
        System.out.println("amount = " + request.getAmount());

        Order order = orderRepository.findByTossOrderIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("주문이 없다."));

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("결제할 수 없는 주문 상태입니다.");
        }

        if(order.getTotalPrice() != request.getAmount()){
            throw new IllegalArgumentException("결제 금액이 일치하지 않습니다.");
        }

        approveTossPayment(request);

        Payment payment = Payment.createPayment(
                order,
                request.getPaymentKey(),
                request.getAmount()
        );

        paymentRepository.save(payment);

        if (order.getUserCoupon() != null) {
            couponService.useCoupon(
                    order.getUser().getUsername(),
                    order.getUserCoupon().getId()
            );
        }

        order.completePayment();

        redisExpirationKeyService.delete(
                "order:pending:" + order.getOrderId()
        );

        return PaymentResponse.from(payment);
    }

    @Transactional
    public void cancelPayment(Order order) {

        Payment payment = paymentRepository.findByOrder_OrderId(order.getOrderId())
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));

        cancelTossPayment(payment.getPaymentKey());

        payment.cancel();
    }

    private void approveTossPayment(PaymentRequest request) {

        try {
            restClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/confirm")
                    .headers(headers ->
                            headers.setBasicAuth(tossSecretKey, "")
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException e) {

            throw new PaymentException(PaymentErrorCode.TOSS_PAYMENT_FAILED);

        } catch (HttpServerErrorException e) {

            throw new PaymentException(PaymentErrorCode.TOSS_SERVER_ERROR);

        } catch (RestClientException e) {

            throw new PaymentException(PaymentErrorCode.PAYMENT_CONNECTION_ERROR);
        }
    }

private void cancelTossPayment(String paymentKey) {

    Map<String, String> body = Map.of(
            "cancelReason", "고객 요청"
    );

    try {
        restClient.post()
                .uri(
                        "https://api.tosspayments.com/v1/payments/{paymentKey}/cancel",
                        paymentKey
                )
                .headers(headers ->
                        headers.setBasicAuth(tossSecretKey, "")
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

    } catch (HttpClientErrorException e) {

        throw new PaymentException(PaymentErrorCode.TOSS_CANCEL_FAILED);

    } catch (HttpServerErrorException e) {

        throw new PaymentException(PaymentErrorCode.TOSS_SERVER_ERROR);

    } catch (RestClientException e) {

        throw new PaymentException(PaymentErrorCode.PAYMENT_CONNECTION_ERROR);
    }
}

}
