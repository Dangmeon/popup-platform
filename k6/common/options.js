// 로컬 기본 동작 확인 모드
const SMOKE = __ENV.SMOKE === 'true';

// 공통 성능 목표
// 별도 설정이 없으면 기본값 사용
const P95_LIMIT = Number(__ENV.P95_LIMIT || 1000);
const ERROR_RATE = Number(__ENV.ERROR_RATE || 0.01);
const CHECK_RATE = Number(__ENV.CHECK_RATE || 0.99);

// 조회 및 일반적인 부하 테스트 설정
export const loadOptions = {

    // 로컬 기본 테스트와 본격적인 부하 테스트 구분
    stages: SMOKE
        ? [
            { duration: '5s', target: 1 },
            { duration: '10s', target: 1 },
            { duration: '5s', target: 0 },
        ]
        : [
            { duration: '30s', target: 10 },
            { duration: '1m', target: 10 },

            { duration: '30s', target: 30 },
            { duration: '1m', target: 30 },

            { duration: '30s', target: 50 },
            { duration: '1m', target: 50 },

            { duration: '30s', target: 100 },
            { duration: '1m', target: 100 },

            { duration: '30s', target: 0 },
        ],

    // 테스트 준비 단계 최대 실행 시간
    setupTimeout: '5m',

    // 공통 성능 판단 기준
    thresholds: {

        // p95 응답 시간 (기본 1000ms 미만)
        'http_req_duration{phase:load}': [
            `p(95)<${P95_LIMIT}`,
        ],

        // HTTP 실패율 (기본 1% 미만)
        'http_req_failed{phase:load}': [
            `rate<${ERROR_RATE}`,
        ],

        // 응답 검증 성공률 (기본 99% 초과)
        checks: [
            `rate>${CHECK_RATE}`,
        ],
    },
};

// 동시성 테스트 공통 설정
// vus: 가상 사용자 수
// expectedSuccess: 예상 성공 요청 수
export function concurrencyOptions(vus, expectedSuccess) {

    // 가상 사용자 수 검증
    if (!Number.isInteger(vus) || vus <= 0) {
        throw new Error('VU 설정 오류');
    }

    // 예상 성공 건수 검증
    if (
        !Number.isInteger(expectedSuccess) ||
        expectedSuccess < 0 ||
        expectedSuccess > vus
    ) {
        throw new Error('예상 성공 건수 설정 오류');
    }

    return {

        setupTimeout: '5m',

        scenarios: {
            concurrency: {

                // 각 가상 사용자가 지정된 횟수만큼 실행
                executor: 'per-vu-iterations',

                vus: vus,

                // 사용자별 1회 실행
                iterations: 1,

                // 최대 실행 시간
                maxDuration: '1m',
            },
        },

        thresholds: {

            // 모든 응답 검증 성공
            checks: ['rate==1'],

            // 전체 반복 실행 횟수 검증
            iterations: [`count==${vus}`],

            // 정상 성공 건수 검증
            success_count: [
                `count==${expectedSuccess}`,
            ],

            // 예상된 비즈니스 예외 건수 검증
            rejected_count: [
                `count==${vus - expectedSuccess}`,
            ],

            // 예상하지 못한 오류 0건
            unexpected_error: ['count==0'],
        },
    };
}