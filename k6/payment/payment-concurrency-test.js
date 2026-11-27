import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, getHeaders, getBody } from '../common/config.js';
import { loginUsers } from '../common/auth.js';
const VUS = Number(__ENV.VUS || 10);
const PRODUCT_ID = Number(__ENV.PRODUCT_ID);

if (!Number.isSafeInteger(PRODUCT_ID) || PRODUCT_ID <= 0) {
    throw new Error('PRODUCT_ID를 설정하세요.');
}

if (!Number.isSafeInteger(VUS) || VUS < 1) {
    throw new Error('VUS는 1 이상의 정수여야 합니다.');
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
        success_count: [`count==${VUS}`],
        unexpected_error: ['count==0'],
    },
};

const successCount = new Counter('success_count');
const unexpectedError = new Counter('unexpected_error');

export function setup() {
    const tokens = loginUsers(VUS);
    const orders = [];

    for (let i = 0; i < VUS; i++) {
        const response = http.post(
            `${BASE_URL}/api/orders`,
            JSON.stringify({
                items: [
                    {
                        prdId: PRODUCT_ID,
                        quantity: 1,
                    },
                ],
            }),
            getHeaders(tokens[i], 'setup')
        );

        const body = getBody(response);

        if (
            (response.status !== 200 && response.status !== 201) ||
            !body?.tossOrderId ||
            !Number.isSafeInteger(body?.totalPrice) ||
            body.totalPrice <= 0
        ) {
            throw new Error(
                `주문 생성 실패: 회원=${i + 1}, status=${response.status}, body=${response.body}`
            );
        }

        orders.push({
            tossOrderId: body.tossOrderId,
            amount: body.totalPrice,
            paymentKey: `mock-concurrent-${body.tossOrderId}`,
        });
    }

    return { tokens, orders };
}

export default function (data) {
    const order = data.orders[__VU - 1];
    const token = data.tokens[__VU - 1];

    const response = http.post(
        `${BASE_URL}/api/payments/confirm`,
        JSON.stringify({
            paymentKey: order.paymentKey,
            orderId: order.tossOrderId,
            amount: order.amount,
        }),
        getHeaders(token)
    );

    const body = getBody(response);

    const success =
        response.status === 200 &&
        body?.paymentId != null &&
        body?.paymentKey === order.paymentKey &&
        body?.paymentStatus === 'PAID' &&
        body?.amount === order.amount;

    successCount.add(success ? 1 : 0);
    unexpectedError.add(success ? 0 : 1);

    check(response, {
        '서로 다른 주문 결제 승인 성공': () => success,
    });
}