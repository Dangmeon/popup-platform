import http from 'k6/http';

import {
    BASE_URL,
    getHeaders,
} from './config.js';

import {
    getAccount,
} from './accounts.js';

// 테스트 회원 한 명 로그인
// 회원 번호를 전달하지 않으면 기본 1번 회원 사용
export function login(index = 1) {

    // 회원 번호에 해당하는 계정 정보 생성
    const account = getAccount(index);

    // LoginRequest DTO에 맞춰 요청 데이터 생성
    const payload = JSON.stringify({
        username: account.username,
        password: account.password,
    });

    // 로그인 API 요청
    const response = http.post(
        `${BASE_URL}/api/auth/login`,
        payload,

        // 로그인 요청은 실제 부하 측정과 구분
        getHeaders(null, 'setup')
    );

    // 로그인 실패 시 테스트 중단
    if (response.status !== 200) {
        throw new Error(
            `회원 ${index} 로그인 실패: HTTP ${response.status}`
        );
    }

    // 로그인 API는 JWT를 문자열로 반환
    const token = String(response.body || '').trim();

    // JWT 기본 형식 확인
    // Header.Payload.Signature
    if (!token || token.split('.').length !== 3) {
        throw new Error(`회원 ${index} JWT 발급 실패`);
    }

    // 발급받은 JWT 반환
    return token;
}

// 여러 테스트 회원 로그인
// 예약, 주문 등의 다중 사용자 동시성 테스트에서 사용
export function loginUsers(count) {

    if (!Number.isInteger(count) || count <= 0) {
        throw new Error('로그인 회원 수 설정 오류');
    }

    const tokens = [];

    // 회원 번호 1번부터 지정된 인원까지 순차 로그인
    for (let i = 1; i <= count; i++) {
        tokens.push(login(i));
    }

    // 회원별 JWT 목록 반환
    return tokens;
}