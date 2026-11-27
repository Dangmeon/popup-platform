import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {BASE_URL, getHeaders, getRequiredId, getBody,} from '../common/config.js';
import { login } from '../common/auth.js';
import { concurrencyOptions } from '../common/options.js';

// 동시 취소 요청 수
const VUS = Number(__ENV.VUS || 10);

// 취소 테스트 대상 주문 ID
const ORDER_ID = getRequiredId('CANCEL_ORDER_ID');

// 동일 주문은 한 번만 취소 가능
const EXPECTED_SUCCESS = 1;

// 공통 동시성 테스트 설정
export const options = concurrencyOptions(
    VUS,
    EXPECTED_SUCCESS
);

// 테스트 결과 집계
const successCount = new Counter('success_count');
const rejectedCount = new Counter('rejected_count');
const unexpectedError = new Counter('unexpected_error');

// 테스트 시작 전 주문 소유자 로그인
export function setup() {
    return {
        token: login(1),
    };
}

export default function (data) {

    // 동일 회원의 JWT 사용
    const token = data.token;

    // 동일 주문에 대한 동시 취소 요청
    const response = http.patch(
        `${BASE_URL}/api/orders/${ORDER_ID}/cancel`,
        null,
        getHeaders(token)
    );

    const body = getBody(response);

    // 정상적인 주문 취소 성공 여부
    const success =
        response.status === 200 &&
        body?.orderId === ORDER_ID &&
        body?.orderStatus === 'CANCELLED';

    // 예상된 중복 주문 취소 거절
    const rejected =
        response.status === 409 && body?.code === 'ORDER_ALREADY_CANCELLED';

    // 예상하지 못한 오류
    const unexpected = !success && !rejected;

    // 테스트 결과 집계
    successCount.add(success ? 1 : 0);
    rejectedCount.add(rejected ? 1 : 0);
    unexpectedError.add(unexpected ? 1 : 0);

    // 응답 검증
    check(response, {
        '주문 취소 성공 또는 정상적인 중복 취소 거절': () => success || rejected,
    });

    // 예상하지 못한 오류 출력
    if (unexpected) {
        console.error(
            `주문 취소 테스트 실패: ` +
            `status=${response.status}, ` +
            `code=${body?.code}, ` +
            `message=${body?.message}`
        );
    }
}