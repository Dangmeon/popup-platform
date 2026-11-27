# POPQ k6 부하 테스트 가이드

## 1. 테스트 목적

k6를 활용하여 POPQ 프로젝트의 API 성능 및 동시성을 검증한다.

공통 테스트 환경을 구축하여 각 도메인 담당자가 동일한 환경에서 테스트를 진행할 수 있도록 한다.

- API 응답 시간 및 처리량 측정
- 동시 사용자 증가에 따른 서버 성능 확인
- 예약, 주문, 쿠폰 등의 데이터 정합성 검증
- 테스트 회원 생성 및 JWT 인증 자동화

## 2. 디렉토리 구조

```
k6/
├── common/
│   ├── config.js
│   ├── options.js
│   ├── accounts.js
│   └── auth.js
├── setup/
│   └── create-users.js
├── smoke/
│   └── auth-smoke-test.js
└── README.md
```

| 파일 | 설명 |
|---|---|
| config.js | 서버 주소, HTTP 요청 설정, 응답 처리 |
| options.js | 부하 테스트 및 동시성 테스트 공통 옵션 |
| accounts.js | 테스트 회원 정보 자동 생성 |
| auth.js | 자동 로그인 및 JWT 발급 |
| create-users.js | 테스트 회원 자동 회원가입 |
| auth-smoke-test.js | 공통 인증 환경 정상 동작 확인 |

각 도메인 담당자는 k6 하위에 담당 도메인 폴더를 생성한 후 테스트 코드를 작성한다.

공통 파일은 중복 작성하지 않고 import하여 사용한다.

## 3. k6 설치 방법

### Windows

PowerShell에서 아래 명령어를 실행한다.

```powershell
winget install k6 --source winget
```

설치가 완료되면 PowerShell을 재실행한다.

### macOS

Homebrew를 사용하는 경우 아래 명령어를 실행한다.

```bash
brew install k6
```

### 설치 확인

```bash
k6 version
```

버전 정보가 출력되면 정상적으로 설치된 것이다.

- 공식 설치 문서: https://grafana.com/docs/k6/latest/set-up/install-k6/
- 공식 다운로드: https://github.com/grafana/k6/releases/latest

Node.js, npm 및 별도의 Maven 의존성은 필요하지 않다.

## 4. 테스트 실행 전 준비 사항

- 최신 develop 브랜치 코드 다운로드
- k6 설치
- 로컬 Spring Boot 백엔드 실행
- Oracle DB 및 Redis 연결 확인
- 테스트 대상 API에 필요한 데이터 준비

로컬 테스트에서는 프론트엔드 실행이나 별도의 서버 배포가 필요하지 않다.

Prometheus 및 Grafana는 본격적인 부하 테스트를 담당하는 환경에서 구성하며, 로컬 기본 동작 확인 시에는 설치하지 않아도 된다.

모든 실행 명령어는 백엔드 프로젝트 루트 디렉토리를 기준으로 한다.

## 5. 환경변수 설정

환경변수는 테스트 환경이나 조건을 변경하기 위해 사용한다.

공통 코드에서는 k6의 `__ENV`를 통해 환경변수를 읽는다.

### 공통 환경변수

| 환경변수 | 기본값 | 필수 여부 | 설명 |
|---|---|---|---|
| BASE_URL | http://localhost:8080 | 선택 | 테스트 대상 서버 주소 |
| TEST_PASSWORD | 없음 | 인증 테스트 필수 | 테스트 회원 비밀번호 |
| USER_COUNT | 100 | 선택 | 자동 생성할 테스트 회원 수 |
| SMOKE | false | 선택 | 로컬 기본 동작 확인 모드 |
| P95_LIMIT | 1000 | 선택 | p95 목표 응답 시간(ms) |
| ERROR_RATE | 0.01 | 선택 | HTTP 실패율 기준 |
| CHECK_RATE | 0.99 | 선택 | 응답 검증 성공률 기준 |

ERROR_RATE와 CHECK_RATE는 백분율이 아닌 소수로 입력한다.

- 0.01 = 1%
- 0.99 = 99%

### 도메인별 환경변수 예시

| 환경변수 | 설명 |
|---|---|
| VUS | 동시성 테스트 가상 사용자 수 |
| POP_ID | 테스트 대상 팝업 ID |
| TIME_SLOT_ID | 테스트 대상 회차 ID |
| RESERVATION_ID | 테스트 대상 예약 ID |
| PRODUCT_ID | 테스트 대상 상품 ID |
| ORDER_ID | 테스트 대상 주문 ID |
| AVAILABLE_CAPACITY | 테스트 회차 잔여 정원 |
| INITIAL_STOCK | 테스트 상품 초기 재고 |

도메인별 환경변수는 각 테스트 스크립트에서 정의하고 읽도록 구현해야 한다. 환경변수를 설정하는 것만으로 모든 테스트에 자동 적용되는 것은 아니다.

### Windows 환경변수 설정

PowerShell에서 실행한다.

```powershell
$env:BASE_URL = "http://localhost:8080"

$env:TEST_PASSWORD = Read-Host "테스트 회원 비밀번호"

$env:USER_COUNT = "10"
```

### macOS 환경변수 설정

기본 zsh 터미널에서 실행한다.

```bash
export BASE_URL=http://localhost:8080

read -s "TEST_PASSWORD?테스트 회원 비밀번호: "
export TEST_PASSWORD

export USER_COUNT=10
```

TEST_PASSWORD는 다음 회원가입 정책을 충족해야 한다.

- 8자 이상 16자 이하
- 영문 포함
- 숫자 포함
- 허용된 특수문자 포함

테스트 회원을 최초 생성할 때 사용한 비밀번호와 이후 로그인 시 사용하는 비밀번호는 동일해야 한다.

환경변수는 현재 터미널 세션에 적용되므로 터미널을 종료하면 다시 설정해야 한다.

### 실행 명령어에서 환경변수 지정

비밀번호 이외의 테스트 조건은 `-e` 옵션을 사용하여 실행 시 지정할 수도 있다.

```bash
k6 run -e USER_COUNT=10 k6/setup/create-users.js
```

```bash
k6 run -e SMOKE=true k6/smoke/auth-smoke-test.js
```

```bash
k6 run -e P95_LIMIT=2000 k6/smoke/auth-smoke-test.js
```

`-e` 옵션으로 전달한 값은 해당 스크립트에서 `__ENV`를 통해 읽어야 한다.

비밀번호는 명령어 실행 기록에 남지 않도록 별도의 환경변수 입력 방식을 사용한다.

## 6. 테스트 회원 자동 생성

다음 명령어를 실행한다.

```bash
k6 run k6/setup/create-users.js
```

USER_COUNT를 10으로 설정한 경우 테스트 회원 10명을 자동으로 생성한다.

### 자동 생성 회원 예시

| 회원 번호 | 이메일 | 이름 |
|---|---|---|
| 1 | popq.k6.test.0001@gmail.com | 테스트회원1 |
| 2 | popq.k6.test.0002@gmail.com | 테스트회원2 |
| 3 | popq.k6.test.0003@gmail.com | 테스트회원3 |

회원별 전화번호도 자동으로 생성된다.

### 회원 생성 처리 과정

1. 테스트 회원 정보 자동 생성
2. 회원가입 API 요청
3. 신규 가입 또는 기존 계정 확인
4. 로그인 API 요청
5. JWT 발급 여부 확인
6. 회원 준비 결과 출력

기존 테스트 회원이 이미 존재하고 동일한 비밀번호로 로그인할 수 있다면 해당 계정을 재사용한다.

### 예상 실행 결과

```
회원 준비 완료: 1
회원 준비 완료: 2
...
회원 준비 완료: 10

==============================
전체 회원 수: 10
준비 성공: 10
준비 실패: 0
==============================
```

신규 DB를 사용하거나 추가 테스트 회원이 필요한 경우 다시 실행한다.

본격적인 동시성 테스트에서 서로 다른 회원 100명이 필요한 경우 USER_COUNT를 100으로 설정한다.

## 7. 공통 인증 Smoke Test

회원 생성 후 아래 명령어를 실행한다.

```bash
k6 run -e SMOKE=true k6/smoke/auth-smoke-test.js
```

### 테스트 실행 과정

1. setup()에서 테스트 회원 자동 로그인
2. JWT 발급
3. 인증 헤더에 JWT 설정
4. 인증 확인 API 요청
5. HTTP 응답 상태 및 응답 내용 검증
6. 테스트 결과 출력

인증 확인 API는 `/api/auth/test`를 사용한다.

해당 API가 SecurityConfig에서 인증을 요구하는 경로인지 확인해야 하며, 인증 없이 접근 가능한 경로라면 JWT 인증 자체를 검증하는 데에는 한계가 있다.

### Smoke Test 설정

| 항목 | 설정값 |
|---|---|
| 최대 VU | 1명 |
| 부하 증가 시간 | 5초 |
| 부하 유지 시간 | 10초 |
| 부하 감소 시간 | 5초 |
| 전체 부하 설정 시간 | 20초 |
| 요청 간 대기 시간 | 1초 |

### 성공 기준

- 자동 로그인 성공
- JWT 발급 성공
- HTTP 200 응답
- 응답 내용 검증 성공
- Threshold 통과

## 8. 공통 테스트 옵션

### Smoke Test

로컬 환경에서 테스트 코드 및 API가 정상적으로 동작하는지 검증한다.

```bash
k6 run -e SMOKE=true 테스트파일.js
```

### Load Test

가상 사용자를 점진적으로 증가시키며 API 성능을 측정한다.

| 단계 | VU | 증가 시간 | 유지 시간 |
|---|---:|---|---|
| 1단계 | 10 | 30초 | 1분 |
| 2단계 | 30 | 30초 | 1분 |
| 3단계 | 50 | 30초 | 1분 |
| 4단계 | 100 | 30초 | 1분 |
| 종료 | 0 | 30초 | - |

전체 부하 설정 시간은 6분 30초이며, 준비 및 종료 처리 시간은 별도이다.

```bash
k6 run -e SMOKE=false 테스트파일.js
```

### Concurrency Test

여러 가상 사용자가 각각 한 번씩 요청하도록 구성하여 동시 요청에 대한 데이터 정합성을 검증한다.

- 예약 정원 초과 방지
- 상품 재고 초과 판매 방지
- 쿠폰 중복 및 초과 발급 방지
- 중복 결제 및 취소 방지

동시성 테스트의 VU 및 예상 성공 건수는 각 테스트에서 설정한다.

per-vu-iterations는 사용자별 실행 횟수를 고정하는 방식이며, 모든 요청이 정확히 같은 순간에 서버에 도착하는 것을 보장하지는 않는다.

## 9. 공통 함수 사용 방법

### 서버 및 HTTP 요청 설정

```javascript
import {
    BASE_URL,
    getHeaders,
    getBody,
    getRequiredId,
} from '../common/config.js';
```

| 함수 및 변수 | 설명 |
|---|---|
| BASE_URL | 테스트 대상 서버 주소 |
| getHeaders(token) | JWT 인증 및 HTTP 요청 설정 반환 |
| getHeaders(null) | 비인증 HTTP 요청 설정 반환 |
| getHeaders(null, 'setup') | 테스트 준비 단계 요청 설정 |
| getBody(response) | JSON 응답 안전하게 반환 |
| getRequiredId(name) | 필수 ID 환경변수 검증 |

getHeaders()는 실제 헤더뿐 아니라 요청 태그와 타임아웃 설정까지 포함한 k6 HTTP 요청 옵션을 반환한다.

### 자동 로그인

```javascript
import {
    login,
    loginUsers,
} from '../common/auth.js';
```

회원 한 명 로그인:

```javascript
export function setup() {
    return {
        token: login(1),
    };
}
```

회원 여러 명 로그인:

```javascript
export function setup() {
    return {
        tokens: loginUsers(100),
    };
}
```

실제 API 요청에서는 setup()에서 전달받은 JWT를 사용한다.

회원 100명을 로그인시키려면 테스트 회원 100명이 미리 생성되어 있어야 한다.

### 공통 부하 설정 사용

```javascript
import { loadOptions } from '../common/options.js';

export const options = loadOptions;
```

### 공통 동시성 설정 사용

```javascript
import { concurrencyOptions } from '../common/options.js';

const VUS = Number(__ENV.VUS || 10);
const EXPECTED_SUCCESS = 5;

export const options =
    concurrencyOptions(VUS, EXPECTED_SUCCESS);
```

첫 번째 인자는 가상 사용자 수이며, 두 번째 인자는 예상 성공 건수이다.

해당 설정을 사용하는 테스트에서는 다음 Counter를 선언해야 한다.

```javascript
import { Counter } from 'k6/metrics';

const successCount = new Counter('success_count');
const rejectedCount = new Counter('rejected_count');
const unexpectedError = new Counter('unexpected_error');
```

각 요청 결과에 따라 세 Counter에 0 또는 1을 기록하여 성공, 정상 거절, 예상하지 못한 오류를 집계한다.

동시성 테스트의 실제 성공 및 실패 조건은 각 도메인 담당자가 구현한다.

## 10. 도메인별 테스트 코드 작성 예시

아래 코드는 공통 설정을 사용하는 기본 템플릿이다.

API_PATH와 응답 검증 조건을 담당 도메인에 맞게 변경해야 한다.

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

import {
    BASE_URL,
    getHeaders,
} from '../common/config.js';

import { login } from '../common/auth.js';
import { loadOptions } from '../common/options.js';

// 공통 부하 설정 사용
export const options = loadOptions;

// 테스트 대상 API 경로
const API_PATH = '/api/auth/test';

// 테스트 시작 전 자동 로그인
export function setup() {
    return {
        token: login(1),
    };
}

// 실제 부하 테스트 로직
export default function (data) {

    const response = http.get(
        `${BASE_URL}${API_PATH}`,
        getHeaders(data.token)
    );

    // 도메인별 응답 검증
    check(response, {
        'API 요청 성공': (res) =>
            res.status === 200,
    });

    // 요청 사이 대기 시간
    sleep(1);
}
```

공통 로그인 함수는 인증이 필요한 API에서 사용하며, 공개 API는 로그인 과정 없이 테스트할 수 있다.

## 11. 테스트 결과 확인

k6 실행 후 터미널에서 다음 성능 지표를 확인할 수 있다.

| 지표 | 설명 |
|---|---|
| http_req_duration | HTTP 응답 시간 |
| avg | 평균 응답 시간 |
| med | 중앙값 |
| p(95) | 전체 요청의 95%가 이 시간 이내에 응답 |
| p(99) | 전체 요청의 99%가 이 시간 이내에 응답 |
| http_req_failed | HTTP 요청 실패율 |
| http_reqs | 전체 HTTP 요청 수 및 초당 요청 수 |
| checks | 응답 검증 성공률 |
| iterations | 테스트 반복 실행 횟수 |
| vus | 현재 가상 사용자 수 |
| vus_max | 최대 가상 사용자 수 |

### 공통 성능 목표

| 항목 | 기본 기준 |
|---|---|
| p95 응답 시간 | 1,000ms 미만 |
| HTTP 요청 실패율 | 1% 미만 |
| 응답 검증 성공률 | 99% 초과 |
| 동시성 데이터 정합성 위반 | 0건 |

성능 기준은 프로젝트의 초기 목표이며 API 특성에 따라 변경할 수 있다.

로그인 및 테스트 준비 요청은 phase:setup, 실제 테스트 요청은 phase:load 태그로 구분한다.

전체 HTTP 지표에는 준비 요청도 포함될 수 있으므로 실제 부하 성능 분석 시 phase:load 지표를 기준으로 확인한다.

동시성 테스트에서 정상적으로 발생한 재고 부족, 예약 정원 초과 등의 비즈니스 예외는 예상하지 못한 오류와 구분하여 집계한다.

## 12. 로컬 및 배포 환경 테스트

| 구분 | 로컬 테스트 | 배포 환경 테스트 |
|---|---|---|
| 목적 | 테스트 코드 정상 동작 확인 | 본격적인 성능 측정 |
| 백엔드 | 로컬 Spring Boot | 배포 서버 |
| BASE_URL | localhost:8080 | 배포 서버 주소 |
| k6 | 필요 | 필요 |
| Prometheus | 선택 | 모니터링 시 필요 |
| Grafana | 선택 | 모니터링 시 필요 |

각 도메인 담당자는 로컬에서 테스트 코드가 정상적으로 동작하는지 확인한 후 PR을 생성한다.

본격적인 부하 테스트는 팀에서 지정한 테스트 환경에서 진행한다.

## 13. 오류 발생 시 확인 사항

| 오류 | 확인 사항 |
|---|---|
| k6 명령어 인식 불가 | 설치 여부 및 터미널 재실행 |
| Connection refused | 백엔드 실행 여부 및 포트 확인 |
| TEST_PASSWORD 설정 오류 | 환경변수 입력 여부 확인 |
| 회원가입 400 | 이메일, 비밀번호 및 요청 데이터 확인 |
| 회원가입 409 | 기존 테스트 계정 확인 |
| 로그인 실패 | 계정 존재 여부 및 비밀번호 확인 |
| HTTP 401/403 | JWT 만료 및 접근 권한 확인 |
| HTTP 404 | API 경로 및 테스트 대상 ID 확인 |
| Threshold 실패 | 성능 기준 및 실제 응답 결과 확인 |
| setupTimeout 초과 | 자동 로그인 소요 시간 확인 |

## 14. 주의사항

- 테스트 전용 계정과 비밀번호를 사용한다.
- 비밀번호 및 JWT를 소스 코드에 하드코딩하지 않는다.
- 실제 운영 데이터를 대상으로 임의의 부하 테스트를 진행하지 않는다.
- 데이터 변경 테스트는 실행 전후 DB 상태를 확인한다.
- 예약 정원, 상품 재고, 쿠폰 수량은 반복 테스트 시 동일한 초기 조건으로 준비한다.
- 결제 부하 테스트는 실제 Toss Payments API가 아닌 Mock 환경에서 진행한다.
- 테스트 회원 이메일 및 전화번호로 실제 메시지를 발송하지 않는다.
- 로컬 기본 동작 확인과 본격적인 부하 테스트는 구분하여 진행한다.

## 15. 참고 문서

- k6 공식 문서: https://grafana.com/docs/k6/latest/
- 설치 방법: https://grafana.com/docs/k6/latest/set-up/install-k6/
- 환경변수: https://grafana.com/docs/k6/latest/using-k6/environment-variables/
- 실행 옵션: https://grafana.com/docs/k6/latest/using-k6/k6-options/reference/
- Scenarios: https://grafana.com/docs/k6/latest/using-k6/scenarios/
- Thresholds: https://grafana.com/docs/k6/latest/using-k6/thresholds/