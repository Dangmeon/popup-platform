import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {BASE_URL, getHeaders, getRequiredId, getBody,} from '../common/config.js';
import { login } from '../common/auth.js';
import { concurrencyOptions } from '../common/options.js';

// 동시 요청 사용자 수
const VUS = Number(__ENV.VUS || 10);

// 구매 제한 테스트 대상 상품
const PRODUCT_ID = getRequiredId('LIMIT_PRODUCT_ID');

// 1인 최대 구매 가능 수량
const MAX_QTY = getRequiredId('MAX_QTY');

// 예상 주문 성공 건수
const EXPECTED_SUCCESS = Math.min(VUS, MAX_QTY);

// 공통 동시성 테스트 설정
export const options = concurrencyOptions(
    VUS,
    EXPECTED_SUCCESS
);

// 테스트 결과 집계
const successCount = new Counter('success_count');
const rejectedCount = new Counter('rejected_count');
const unexpectedError = new Counter('unexpected_error');

// 테스트 시작 전 회원 한 명 로그인
export function setup() {
    return {
        token: login(1),
    };
}

export default function (data) {

    // 모든 가상 사용자가 동일한 JWT 사용
    const token = data.token;

    // 주문 요청 데이터 생성
    const payload = JSON.stringify({
        items: [
            {
                prdId: PRODUCT_ID,
                quantity: 1,
            },
        ],

        receiverName: '테스트회원',
        receiverPhone: '01099990000',
        deliveryAddress: '테스트 배송지',
        deliveryAddressDetail: '테스트 상세주소',
        deliveryMemo: 'k6 구매 제한 테스트',
    });

    // 동일 회원의 동일 상품 주문 요청
    const response = http.post(
        `${BASE_URL}/api/orders`,
        payload,
        getHeaders(token)
    );

    const body = getBody(response);

    // 정상 주문 성공 여부
    const success =
        response.status === 200 &&
        body?.orderId != null &&
        body?.tossOrderId != null &&
        body?.orderStatus === 'PENDING' &&
        typeof body?.totalPrice === 'number' &&
        body.totalPrice >= 0;

    // 예상된 구매 제한 초과 예외
    const rejected =
        response.status === 409 &&
        body?.code === 'PURCHASE_LIMIT_EXCEEDED';

    // 예상하지 못한 오류
    const unexpected = !success && !rejected;

    // 요청 결과 집계
    successCount.add(success ? 1 : 0);
    rejectedCount.add(rejected ? 1 : 0);
    unexpectedError.add(unexpected ? 1 : 0);

    // 응답 검증
    check(response, {
        '주문 성공 또는 정상적인 구매 제한 거절': () => success || rejected,
    });

    // 예상하지 못한 오류 출력
    if (unexpected) {
        console.error(
            `구매 제한 테스트 실패: ` +
            `status=${response.status}, ` +
            `code=${body?.code}, ` +
            `message=${body?.message}`
        );
    }
}