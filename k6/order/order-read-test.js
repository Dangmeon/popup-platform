import http from 'k6/http';
import { check, sleep } from 'k6';
import {BASE_URL, getHeaders, getBody,} from '../common/config.js';
import { login } from '../common/auth.js';
import { loadOptions } from '../common/options.js';

// 공통 부하 테스트 설정
export const options = loadOptions;

// 테스트 시작 전 자동 로그인
export function setup() {
    return {
        token: login(1),
    };
}

export default function (data) {

    // 본인의 주문 목록 조회 요청
    const response = http.get(
        `${BASE_URL}/api/orders`,
        {
            ...getHeaders(data.token),
            tags: {
                phase: 'load',
                name: 'GET /api/orders',
            },
        }
    );

    // 응답 데이터 파싱
    const body = getBody(response);

    // 조회 실패 원인 확인
    if (response.status !== 200) {
        console.error(
            `주문 목록 조회 실패: ` +
            `status=${response.status}, ` +
            `code=${body?.code}, ` +
            `message=${body?.message}`
        );
    }

    // 주문 목록 응답 검증
    check(response, {

        // HTTP 정상 응답 여부
        '주문 목록 조회 성공': (res) =>
            res.status === 200,

        // 주문 목록 배열 반환 여부
        '주문 목록 배열 반환': () =>
            Array.isArray(body),

        // 실제 주문 데이터 존재 여부
        '주문 데이터 존재': () =>
            Array.isArray(body) &&
            body.length > 0,

        // 주문 데이터 유효성 검증
        '주문 데이터 정상': () =>
            Array.isArray(body) &&
            body.length > 0 &&
            body.every(order =>
                order.orderId != null &&
                order.orderStatus != null &&
                typeof order.totalPrice === 'number' &&
                order.totalPrice >= 0
            ),
    });

    // 요청 사이 1초 대기
    sleep(1);
}