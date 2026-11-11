package com.popq.platform.service;

import com.popq.platform.dto.PaymentRequest;
import com.popq.platform.dto.PaymentResponse;
import com.popq.platform.dto.TossErrorResponse;
import com.popq.platform.entity.Order;
import com.popq.platform.entity.OrderStatus;
import com.popq.platform.entity.Payment;
import com.popq.platform.exception.PaymentException;
import com.popq.platform.redis.RedisExpirationKeyService;
import com.popq.platform.repository.OrderRepository;
import com.popq.platform.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final RedisExpirationKeyService redisExpirationKeyService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${toss.secret-key}")
    private String tossSecretKey;

    @Transactional
    public PaymentResponse confirmPayment(PaymentRequest request) {

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

        order.completePayment();

        redisExpirationKeyService.delete(
                "order:pending:" + order.getOrderId()
        );

        return PaymentResponse.from(payment);
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

            try {
                TossErrorResponse tossError =
                        objectMapper.readValue(
                                e.getResponseBodyAsString(),
                                TossErrorResponse.class
                        );

                throw new PaymentException(
                        tossError.getCode(),
                        tossError.getMessage()
                );

            } catch (JacksonException parsingException) {

                throw new PaymentException(
                        "TOSS_PAYMENT_FAILED",
                        "결제 승인에 실패했습니다."
                );
            }

        } catch (HttpServerErrorException e) {

            throw new PaymentException(
                    "TOSS_SERVER_ERROR",
                    "토스페이먼츠 서버 오류가 발생했습니다."
            );

        } catch (RestClientException e) {

            throw new PaymentException(
                    "PAYMENT_CONNECTION_ERROR",
                    "결제 서버와 통신 중 오류가 발생했습니다."
            );
        }
    }

    // TODO TossPayments 연동 후 결제 취소 구현
}
