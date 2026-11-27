import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {BASE_URL, getHeaders, getRequiredId, getBody,} from '../common/config.js';
import { login } from '../common/auth.js';
import { concurrencyOptions } from '../common/options.js';

// 동시 요청을 보낼 가상 사용자 수
const VUS = Number(__ENV.VUS || 10);

// 중복 예약 테스트 대상 회차 ID
const TIME_SLOT_ID =
    getRequiredId('DUPLICATE_TIME_SLOT_ID');

// 동일 회원은 동일 회차에 한 번만 예약 가능
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

// 테스트 시작 전 회원 한 명 로그인
export function setup() {

    return {
        token: login(1),
    };
}

export default function (data) {

    // 모든 가상 사용자가 동일한 JWT 사용
    const token = data.token;

    // 동일 회차에 동일한 예약 요청 생성
    const payload = JSON.stringify({
        timeSlotId: TIME_SLOT_ID,
        peopleCount: 1,
    });

    // 예약 생성 API 요청
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

    // 예상된 중복 예약 예외 여부
    const rejected = response.status === 409 && body?.code === 'DUPLICATE_RESERVATION';

    // 예상하지 못한 오류 여부
    const unexpected = !success && !rejected;

    // 요청 결과별 집계
    successCount.add(success ? 1 : 0);
    rejectedCount.add(rejected ? 1 : 0);
    unexpectedError.add(unexpected ? 1 : 0);

    // 정상 예약 또는 중복 예약 거절 검증
    check(response, {
        '예약 성공 또는 정상적인 중복 예약 거절': () => success || rejected,
    });

    // 예상하지 못한 오류 출력
    if (unexpected) {
        console.error(
            `중복 예약 테스트 실패: ` +
            `status=${response.status}, ` +
            `code=${body?.code}, ` +
            `message=${body?.message}`
        );
    }
}