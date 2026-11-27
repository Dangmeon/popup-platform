import http from 'k6/http';
import { check, sleep } from 'k6';
import {BASE_URL, getHeaders, getRequiredId, getBody,} from '../common/config.js';
import { login } from '../common/auth.js';
import { loadOptions } from '../common/options.js';

// 공통 부하 테스트 설정
export const options = loadOptions;

// 테스트 대상 예약 ID
const RESERVATION_ID = getRequiredId('RESERVATION_ID');

// 테스트 시작 전 자동 로그인
export function setup() {
    return {
        token: login(1),
    };
}

export default function (data) {

    // 예약 상세 조회 API 요청
    const response = http.get(
        `${BASE_URL}/api/reservations/${RESERVATION_ID}`,
        {
            ...getHeaders(data.token),

            // 예약 ID와 관계없이 동일 API로 성능 집계
            tags: {
                phase: 'load',
                name: 'GET /api/reservations/:reservationId',
            },
        }
    );

    // 응답 데이터 파싱
    const body = getBody(response);

    // 예약 상세 조회 실패 원인 확인
    if (response.status !== 200) {
        console.error(
            `예약 상세 조회 실패: ` +
            `status=${response.status}, ` +
            `code=${body?.code}, ` +
            `message=${body?.message}`
        );
    }

    // 예약 상세 응답 검증
    check(response, {

        // 정상 응답 여부
        '예약 상세 조회 성공': (res) =>
            res.status === 200,

        // 요청한 예약과 응답 예약 ID 일치 여부
        '예약 ID 일치': () =>
            body?.reservationId === RESERVATION_ID,

        // 예약 회차 정보 존재 여부
        '예약 회차 정보 존재': () =>
            body?.timeSlotId != null,

        // 예약 인원 유효성 검증
        '예약 인원 정상': () =>
            Number.isInteger(body?.peopleCount) &&
            body.peopleCount >= 1 &&
            body.peopleCount <= 3,
    });

    // 요청 사이 1초 대기
    sleep(1);
}