import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {BASE_URL, getHeaders, getBody,} from '../common/config.js';
import { login } from '../common/auth.js';

// 동일 주문에 대한 동시 승인 요청 수
const VUS = Number(__ENV.VUS || 10);

// 결제 테스트 데이터
const TOSS_ORDER_ID = __ENV.TOSS_ORDER_ID;
const PAYMENT_KEY = __ENV.PAYMENT_KEY;
const PAYMENT_AMOUNT = Number(__ENV.PAYMENT_AMOUNT);

// 환경변수 검증
if (
    !Number.isSafeInteger(VUS) ||
    VUS < 1 ||
    !TOSS_ORDER_ID ||
    !PAYMENT_KEY ||
    !Number.isSafeInteger(PAYMENT_AMOUNT) ||
    PAYMENT_AMOUNT <= 0
) {
    throw new Error(
        'VUS, TOSS_ORDER_ID, PAYMENT_KEY, PAYMENT_AMOUNT 설정을 확인해 주세요.'
    );
}

// 동시성 테스트 설정
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
        success_count: ['count>=1'],
        unexpected_error: ['count==0'],
    },
};

// 결과 집계
const successCount = new Counter('success_count');
const rejectedCount = new Counter('rejected_count');
const unexpectedError = new Counter('unexpected_error');

// 동일 주문 소유자 로그인
export function setup() {
    return {
        token: login(1),
    };
}

export default function (data) {

    // 모든 VU가 같은 결제 정보를 전송
    const payload = JSON.stringify({
        paymentKey: PAYMENT_KEY,
        orderId: TOSS_ORDER_ID,
        amount: PAYMENT_AMOUNT,
    });

    const response = http.post(
        `${BASE_URL}/api/payments/confirm`,
        payload,
        getHeaders(data.token)
    );

    const body = getBody(response);

    // 정상 승인 또는 기존 결제 결과 반환
    const success =
        response.status === 200 &&
        body?.paymentId != null &&
        body?.paymentKey === PAYMENT_KEY &&
        body?.amount === PAYMENT_AMOUNT &&
        body?.paymentStatus === 'PAID';

    // 처리 중 중복 승인 요청 거절
    const rejected = response.status === 409 && body?.code === 'PAYMENT_PROCESSING';

    // 예상하지 못한 오류
    const unexpected = !success && !rejected;

    // 집계
    successCount.add(success ? 1 : 0);
    rejectedCount.add(rejected ? 1 : 0);
    unexpectedError.add(unexpected ? 1 : 0);

    check(response, {
        '결제 승인 또는 정상적인 중복 처리': () => success || rejected,
    });

    if (unexpected) {
        console.error(
            `중복 결제 승인 실패: ` +
            `status=${response.status}, ` +
            `code=${body?.code}, ` +
            `message=${body?.message}`
        );
    }
}