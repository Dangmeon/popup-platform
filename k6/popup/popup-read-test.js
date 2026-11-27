// 팝업 · 상품 · 회차 조회 성능 테스트
// 대상 : 팝업 목록, 팝업 상세, 상품 목록, 예약 가능 회차 조회 (로그인 없이 호출하는 공개 GET API)
// 로컬 확인 : k6 run -e SMOKE=true k6/popup/popup-read-test.js
// 부하 테스트 : k6 run -e SMOKE=false k6/popup/popup-read-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';

import { BASE_URL, getHeaders, getBody } from '../common/config.js';
import { loadOptions } from '../common/options.js';

const P95_LIMIT = Number(__ENV.P95_LIMIT || 1000);

const CATEGORIES = ['FASHION', 'CHAR', 'ENT', 'FOOD', 'LIVING', 'SPORTS', 'ETC'];
const STATUSES = ['UPCOMING', 'ONGOING', 'ENDED'];

// 공통 부하 설정 + API별 p95 기준 (어느 API가 느린지 따로 보기 위해)
export const options = {
    ...loadOptions,
    thresholds: {
        ...loadOptions.thresholds,
        'http_req_duration{api:popup_list}': [`p(95)<${P95_LIMIT}`],
        'http_req_duration{api:popup_detail}': [`p(95)<${P95_LIMIT}`],
        'http_req_duration{api:product_list}': [`p(95)<${P95_LIMIT}`],
        'http_req_duration{api:slot_list}': [`p(95)<${P95_LIMIT}`],
    },
};

// 공통 요청 설정(phase:load 태그)에 API 이름 태그를 추가
function apiParams(apiName) {
    const params = getHeaders(null); // 공개 API라 토큰 없음
    params.tags.api = apiName;
    return params;
}

// 테스트 시작 전에 한 번 실행 : 실제 DB에 있는 팝업 ID 목록을 가져옴 (POP_ID를 따로 넣지 않아도 됨)
export function setup() {
    const response = http.get(`${BASE_URL}/api/popups`, getHeaders(null, 'setup'));
    const body = getBody(response);
    if (response.status !== 200 || !Array.isArray(body)) {
        throw new Error(`팝업 목록 조회 실패 (status ${response.status}) : 서버 실행 여부를 확인해 주세요.`);
    }
    const popIds = [];
    for (const popup of body) {
        popIds.push(popup.popId);
    }
    if (popIds.length === 0) {
        throw new Error('팝업이 없습니다 : 시드 데이터를 먼저 넣어 주세요.');
    }
    console.log(`테스트 대상 팝업 ${popIds.length}개`);
    return { popIds: popIds };
}

function pick(list) {
    return list[Math.floor(Math.random() * list.length)];
}

// 오늘 날짜 (yyyy-MM-dd)
function today() {
    const d = new Date();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${month}-${day}`;
}

// 가상 사용자 1명이 반복하는 흐름 : 목록 -> 상세 -> 상품 -> 회차 (실제 사용자가 화면을 보는 순서)
export default function (data) {
    // 1. 팝업 목록 : 전체, 카테고리, 상태, 인기순 중 하나로 조회
    const listUrls = [
        `${BASE_URL}/api/popups`,
        `${BASE_URL}/api/popups?category=${pick(CATEGORIES)}`,
        `${BASE_URL}/api/popups?status=${pick(STATUSES)}`,
        `${BASE_URL}/api/popups?sort=POPULAR`,
    ];
    const listRes = http.get(pick(listUrls), apiParams('popup_list'));
    check(listRes, {
        '팝업 목록 200': (res) => res.status === 200,
        '팝업 목록 배열 응답': (res) => Array.isArray(getBody(res)),
    });

    const popId = pick(data.popIds);

    // 2. 팝업 상세
    const detailRes = http.get(`${BASE_URL}/api/popups/${popId}`, apiParams('popup_detail'));
    check(detailRes, {
        '팝업 상세 200': (res) => res.status === 200,
        '팝업 상세 popId 일치': (res) => getBody(res) !== null && getBody(res).popId === popId,
    });

    // 3. 상품 목록
    const productRes = http.get(`${BASE_URL}/api/popups/${popId}/products`, apiParams('product_list'));
    check(productRes, {
        '상품 목록 200': (res) => res.status === 200,
    });

    // 4. 예약 가능 회차 (오늘 날짜)
    const slotRes = http.get(`${BASE_URL}/api/popups/${popId}/slots?date=${today()}`, apiParams('slot_list'));
    check(slotRes, {
        '회차 목록 200': (res) => res.status === 200,
    });

    sleep(1); // 요청 사이 대기 시간 (실제 사용자가 화면을 보는 시간)
}