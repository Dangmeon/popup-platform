import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

import {
    BASE_URL,
    getHeaders,
    getBody,
    getRequiredId,
} from '../common/config.js';
import { loginUsers } from '../common/auth.js';
import { concurrencyOptions } from '../common/options.js';

// 동시성 테스트 설정
const VUS = Number(__ENV.VUS || 10);
// 웨이팅은 정원 제한 없이 전원 대기번호를 정상 발급받으므로 EXPECTED_SUCCESS = VUS
const EXPECTED_SUCCESS = Number(__ENV.EXPECTED_SUCCESS || VUS);

export const options = concurrencyOptions(VUS, EXPECTED_SUCCESS);

// 집계 Counter
const successCount = new Counter('success_count');
const rejectedCount = new Counter('rejected_count');
const unexpectedError = new Counter('unexpected_error');

const API_PATH = '/api/waitings';

// 테스트 시작 전 VUS 수만큼 테스트 회원 자동 로그인
export function setup() {
    const popId = getRequiredId('POP_ID');
    const tokens = loginUsers(VUS);
    return { popId, tokens };
}

export default function (data) {
    // VU 번호(1 ~ VUS)에 해당하는 고유 회원 토큰 및 고유 전화번호 매핑
    const userIndex = (__VU - 1) % data.tokens.length;
    const token = data.tokens[userIndex];
    const phoneSuffix = String(__VU).padStart(4, '0');

    const payload = JSON.stringify({
        popId: data.popId,
        phone: `010-7700-${phoneSuffix}`,
        peopleCount: 2,
    });

    const response = http.post(
        `${BASE_URL}${API_PATH}`,
        payload,
        getHeaders(token, 'load')
    );

    const body = getBody(response);

    if (response.status === 201 && body && body.queueNumber > 0) {
        successCount.add(1);
        rejectedCount.add(0);
        unexpectedError.add(0);
    } else if (response.status === 400 || response.status === 409) {
        // 중복 대기 등 정상 비즈니스 예외 거절
        successCount.add(0);
        rejectedCount.add(1);
        unexpectedError.add(0);
    } else {
        successCount.add(0);
        rejectedCount.add(0);
        unexpectedError.add(1);
    }

    check(response, {
        '대기 등록 성공 (201) 및 대기번호 발급': (res) =>
            res.status === 201 && body !== null && body.queueNumber > 0,
    });
}
