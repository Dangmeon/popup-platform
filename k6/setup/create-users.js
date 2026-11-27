import http from 'k6/http';
import { check } from 'k6';

import {
    BASE_URL,
    getHeaders,
    getBody,
} from '../common/config.js';

import {
    getAccount,
} from '../common/accounts.js';

import {
    login,
} from '../common/auth.js';

// 생성할 테스트 회원 수
// 환경변수가 없으면 기본 100명 생성
const USER_COUNT = Number(__ENV.USER_COUNT || 100);

// 회원 수 유효성 검증
if (
    !Number.isInteger(USER_COUNT) ||
    USER_COUNT <= 0 ||
    USER_COUNT > 9999
) {
    throw new Error('USER_COUNT 설정 오류');
}

// 부하 테스트가 아닌 테스트 데이터 준비 작업
export const options = {
    scenarios: {
        create_users: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '10m',
        },
    },

    thresholds: {
        // 회원 준비 검증 성공률 100%
        checks: ['rate==1'],
    },
};

export default function () {

    // 회원 준비 결과 집계
    let successCount = 0;
    let failedCount = 0;

    // 지정한 회원 수만큼 반복
    for (let i = 1; i <= USER_COUNT; i++) {

        // 회원별 가입 정보 생성
        const account = getAccount(i);

        // 회원가입 API 요청
        const response = http.post(
            `${BASE_URL}/api/auth/signup`,
            JSON.stringify(account),
            getHeaders(null, 'setup')
        );

        let ready = false;

        // 신규 가입 성공 또는 기존 회원 충돌
        if (
            response.status === 200 ||
            response.status === 409
        ) {
            try {
                // 실제 로그인까지 성공하는지 확인
                // 기존 계정인 경우에도 동일하게 검증
                login(i);
                ready = true;

            } catch (error) {
                ready = false;

                // 회원가입은 성공했지만 로그인에 실패한 경우
                // 기존 계정의 비밀번호 불일치 및 인증 오류 확인
                console.error(
                    `회원 ${i} 로그인 실패: ${error.message}`
                );
            }
        }

        // 테스트 회원이 실제 사용 가능한지 검증
        check(response, {
            '테스트 계정 준비 성공': () => ready,
        });

        if (ready) {

            successCount++;

            console.log(`회원 준비 완료: ${i}`);

        } else {

            failedCount++;

            console.error(
                `회원 준비 실패: ${i}, ` +
                `status=${response.status}, ` +
                `code=${getBody(response)?.code}`
            );
        }
    }

    // 최종 테스트 회원 준비 결과 출력
    console.log('==============================');
    console.log(`전체 회원 수: ${USER_COUNT}`);
    console.log(`준비 성공: ${successCount}`);
    console.log(`준비 실패: ${failedCount}`);
    console.log('==============================');
}