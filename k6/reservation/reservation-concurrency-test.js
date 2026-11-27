import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import {BASE_URL, getHeaders, getRequiredId, getBody,} from '../common/config.js';
import { loginUsers } from '../common/auth.js';
import { concurrencyOptions } from '../common/options.js';

// 동시 요청을 보낼 가상 사용자 수
const VUS = Number(__ENV.VUS || 10);

// 테스트 대상 회차 ID
const TIME_SLOT_ID = getRequiredId('TIME_SLOT_ID');

// 테스트 실행 전 회차의 잔여 정원
const AVAILABLE_CAPACITY = Number(__ENV.AVAILABLE_CAPACITY);

if (
    !Number.isInteger(AVAILABLE_CAPACITY) || AVAILABLE_CAPACITY < 0
) {
    throw new Error('AVAILABLE_CAPACITY 설정 오류');
}

// 예상 예약 성공 건수
const EXPECTED_SUCCESS = Math.min(
    VUS,
    AVAILABLE_CAPACITY
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

    // 예약 요청 데이터 생성
    const payload = JSON.stringify({
        timeSlotId: TIME_SLOT_ID,
        peopleCount: 1,
    });

    // 동일 회차에 예약 요청
    const response = http.post(
        `${BASE_URL}/api/reservations`, payload, getHeaders(token)
    );

    // 응답 데이터 파싱
    const body = getBody(response);

    // 정상 예약 성공 여부
    const success =
        response.status === 200 &&
        body?.reservationId != null &&
        body?.reservationStatus === 'HOLD' &&
        body?.timeSlotId === TIME_SLOT_ID &&
        body?.peopleCount === 1;

    // 예상된 정원 초과 예외 여부
    const rejected =
        response.status === 409 &&
        body?.code === 'CAPACITY_EXCEEDED';

    // 예상하지 못한 오류 여부
    const unexpected = !success && !rejected;

    // 요청 결과별 집계
    successCount.add(success ? 1 : 0);
    rejectedCount.add(rejected ? 1 : 0);
    unexpectedError.add(unexpected ? 1 : 0);

    // 정상 예약 또는 예상된 정원 초과만 허용
    check(response, {
        '예약 성공 또는 정상적인 정원 초과 거절': () => success || rejected,
    });

    // 예상하지 못한 오류 발생 시 원인 출력
    if (unexpected) {
        console.error(
            `예약 요청 실패: ` +
            `status=${response.status}, ` +
            `code=${body?.code}, ` +
            `message=${body?.message}`
        );
    }
}