import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import {BASE_URL, getHeaders, getRequiredId, getBody,} from '../common/config.js';
import { loginUsers } from '../common/auth.js';
import { concurrencyOptions } from '../common/options.js';

// 동시 요청을 보낼 가상 사용자 수
const VUS = Number(__ENV.VUS || 10);

// 테스트 대상 상품 ID
const PRODUCT_ID = getRequiredId('PRODUCT_ID');

// 테스트 시작 전 상품 재고
const INITIAL_STOCK = Number(__ENV.INITIAL_STOCK);

if (
    !Number.isInteger(INITIAL_STOCK) || INITIAL_STOCK < 0
) {
    throw new Error('INITIAL_STOCK 설정 오류');
}

// 예상 주문 성공 건수
const EXPECTED_SUCCESS = Math.min(
    VUS,
    INITIAL_STOCK
);

// 공통 동시성 테스트 설정
export const options = concurrencyOptions(
    VUS,
    EXPECTED_SUCCESS
);

// 테스트 결과 집계
const successCount = new Counter('success_count');
const rejectedCount = new Counter('rejected_count');
const unexpectedError = new Counter('unexpected_error');

// 테스트 시작 전 회원별 로그인
export function setup() {
    return {
        tokens: loginUsers(VUS),
    };
}

export default function (data) {

    // 가상 사용자마다 서로 다른 회원의 JWT 사용
    const token = data.tokens[exec.vu.idInTest - 1];

    // OrderCreateRequest DTO에 맞춰 주문 요청 생성
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
        deliveryMemo: 'k6 재고 동시성 테스트',
    });

    // 동일 상품에 대한 주문 생성 요청
    const response = http.post(
        `${BASE_URL}/api/orders`,
        payload,
        getHeaders(token)
    );

    // 응답 데이터 파싱
    const body = getBody(response);

    // 정상 주문 성공 여부
    const success =
        response.status === 200 &&
        body?.orderId != null &&
        body?.tossOrderId != null &&
        body?.orderStatus === 'PENDING' &&
        typeof body?.totalPrice === 'number' &&
        body.totalPrice >= 0;

    // 예상된 재고 부족 예외 여부
    const rejected =
        response.status === 409 &&
        body?.code === 'INSUFFICIENT_STOCK';

    // 예상하지 못한 오류 여부
    const unexpected = !success && !rejected;

    // 요청 결과별 집계
    successCount.add(success ? 1 : 0);
    rejectedCount.add(rejected ? 1 : 0);
    unexpectedError.add(unexpected ? 1 : 0);

    // 정상 주문 또는 예상된 재고 부족 검증
    check(response, {
        '주문 성공 또는 정상적인 재고 부족 거절': () => success || rejected,
    });

    // 예상하지 못한 오류 출력
    if (unexpected) {
        console.error(
            `주문 요청 실패: ` +
            `status=${response.status}, ` +
            `code=${body?.code}, ` +
            `message=${body?.message}`
        );
    }
}