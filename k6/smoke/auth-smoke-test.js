import http from 'k6/http';
import { check, sleep } from 'k6';

import {
    BASE_URL,
    getHeaders,
} from '../common/config.js';

import {
    login,
} from '../common/auth.js';

import {
    loadOptions,
} from '../common/options.js';

// 공통 부하 테스트 설정 사용
export const options = loadOptions;

// 테스트 시작 전 실행
export function setup() {

    // 테스트 회원 1번 로그인 및 JWT 발급
    const token = login(1);

    // 실제 테스트 함수로 JWT 전달
    return {
        token: token,
    };
}

// 가상 사용자가 반복 실행하는 테스트 로직
export default function (data) {

    // JWT를 포함하여 인증 확인 API 요청
    const response = http.get(
        `${BASE_URL}/api/auth/test`,
        getHeaders(data.token)
    );

    // 응답 상태 및 인증 성공 메시지 확인
    check(response, {

        'HTTP 200 응답 성공': (res) =>
            res.status === 200,

        'JWT 인증 성공': (res) =>
            String(res.body || '').includes('인증 성공'),
    });

    // 요청 사이 1초 대기
    sleep(1);
}