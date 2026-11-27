import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {BASE_URL, getHeaders, getBody,} from '../common/config.js';
import { login } from '../common/auth.js';
import { concurrencyOptions } from '../common/options.js';

// 결제 승인 테스트는 먼저 1건만 실행
export const options = concurrencyOptions(1, 1);

// 테스트 주문 정보
const TOSS_ORDER_ID = __ENV.TOSS_ORDER_ID;
const PAYMENT_KEY = __ENV.PAYMENT_KEY;
const PAYMENT_AMOUNT = Number(__ENV.PAYMENT_AMOUNT);

// 환경변수 검증
if (!TOSS_ORDER_ID || !PAYMENT_KEY ||
    !Number.isSafeInteger(PAYMENT_AMOUNT) ||
    PAYMENT_AMOUNT <= 0) {
    throw new Error(
        'TOSS_ORDER_ID, PAYMENT_KEY, PAYMENT_AMOUNT 설정을 확인해 주세요.'
    );
}

// 결과 집계
const successCount = new Counter('success_count');
const rejectedCount = new Counter('rejected_count');
const unexpectedError = new Counter('unexpected_error');

// 주문 소유자 로그인
export function setup() {
    return {
        token: login(1),
    };
}

export default function (data) {

    const payload = JSON.stringify({
        paymentKey: PAYMENT_KEY,
        orderId: TOSS_ORDER_ID,
        amount: PAYMENT_AMOUNT,
    });

    // 결제 승인 요청
    const response = http.post(
        `${BASE_URL}/api/payments/confirm`,
        payload,
        getHeaders(data.token)
    );

    const body = getBody(response);

    // 결제 승인 성공 검증
    const success =
        response.status === 200 &&
        body?.paymentId != null &&
        body?.paymentKey === PAYMENT_KEY &&
        body?.amount === PAYMENT_AMOUNT &&
        body?.paymentStatus === 'PAID';

    const unexpected = !success;

    successCount.add(success ? 1 : 0);
    rejectedCount.add(0);
    unexpectedError.add(unexpected ? 1 : 0);

    check(response, {
        '결제 승인 성공 및 응답 데이터 검증': () => success,
    });

    if (unexpected) {
        console.error(
            `결제 승인 실패: ` +
            `status=${response.status}, ` +
            `code=${body?.code}, ` +
            `message=${body?.message}`
        );
    }
}