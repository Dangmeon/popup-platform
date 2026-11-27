import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, getHeaders, getBody } from '../common/config.js';
import { login } from '../common/auth.js';
const VUS = Number(__ENV.VUS || 10);
const PRODUCT_ID = Number(__ENV.PRODUCT_ID);

if (!Number.isSafeInteger(VUS) || VUS < 1) {
    throw new Error('VUS는 1 이상의 정수여야 합니다.');
}

if (!Number.isSafeInteger(PRODUCT_ID) || PRODUCT_ID <= 0) {
    throw new Error('PRODUCT_ID를 설정하세요.');
}

export const options = {
    setupTimeout: '5m',
    scenarios: {
        concurrency: {
            executor: 'per-vu-iterations',
            vus: VUS,
            iterations: 1,
            maxDuration: '1m',
        },
    },
    thresholds: {
        checks: ['rate==1'],
        iterations: [`count==${VUS}`],
        success_count: ['count==1'],
        rejected_count: [`count==${VUS - 1}`],
        unexpected_error: ['count==0'],
    },
};

const successCount = new Counter('success_count');
const rejectedCount = new Counter('rejected_count');
const unexpectedError = new Counter('unexpected_error');

export function setup() {
    const token = login(1);

    // 1. 테스트 주문 자동 생성
    const orderResponse = http.post(
        `${BASE_URL}/api/orders`,
        JSON.stringify({
            items: [
                {
                    prdId: PRODUCT_ID,
                    quantity: 1,
                },
            ],
        }),
        getHeaders(token, 'setup')
    );

    const order = getBody(orderResponse);

    if (
        (orderResponse.status !== 200 && orderResponse.status !== 201) ||
        !Number.isSafeInteger(order?.orderId) ||
        !order?.tossOrderId ||
        !Number.isSafeInteger(order?.totalPrice) ||
        order.totalPrice <= 0
    ) {
        throw new Error(
            `주문 생성 실패: status=${orderResponse.status}, body=${orderResponse.body}`
        );
    }

    const paymentKey = `mock-cancel-${order.tossOrderId}`;

    // 2. WireMock으로 결제 승인
    const paymentResponse = http.post(
        `${BASE_URL}/api/payments/confirm`,
        JSON.stringify({
            paymentKey,
            orderId: order.tossOrderId,
            amount: order.totalPrice,
        }),
        getHeaders(token, 'setup')
    );

    const payment = getBody(paymentResponse);

    if (
        paymentResponse.status !== 200 ||
        payment?.paymentStatus !== 'PAID' ||
        payment?.paymentKey !== paymentKey
    ) {
        throw new Error(
            `결제 승인 실패: status=${paymentResponse.status}, body=${paymentResponse.body}`
        );
    }

    console.log(`취소 테스트 준비 완료: orderId=${order.orderId}`);

    return {
        token,
        orderId: order.orderId,
        paymentKey,
    };
}

export default function (data) {
    // 3. 동일 주문에 취소 요청 동시 전송
    const response = http.patch(
        `${BASE_URL}/api/orders/${data.orderId}/cancel`,
        null,
        getHeaders(data.token)
    );

    const body = getBody(response);

    const success =
        response.status === 200 &&
        body?.orderStatus === 'CANCELLED';

    const rejected =
        response.status === 409 &&
        body?.code === 'ORDER_ALREADY_CANCELLED';

    const unexpected = !success && !rejected;

    successCount.add(success ? 1 : 0);
    rejectedCount.add(rejected ? 1 : 0);
    unexpectedError.add(unexpected ? 1 : 0);

    check(response, {
        '결제 취소 또는 정상적인 중복 취소 거절': () => success || rejected,
    });

    if (unexpected) {
        console.error(
            `취소 실패: VU=${__VU}, status=${response.status}, ` +
            `code=${body?.code}, message=${body?.message}`
        );
    }
}
