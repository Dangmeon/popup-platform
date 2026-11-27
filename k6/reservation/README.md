# 예약 API k6 테스트

## 1. 예약 상세 조회 테스트

## 공통 실행 조건

- k6 설치 및 환경변수 설정은 상위 k6/README.md 참고
- 모든 명령어는 k6 디렉토리에서 실행
- 배포 환경에서는 BASE_URL을 테스트 서버 주소로 변경
- 테스트 회원은 setup/create-users.js를 통해 사전 생성
- 예약 가능한 팝업 및 회차 데이터 사전 준비
- 실제 운영 환경이 아닌 테스트 전용 환경에서 실행

### 테스트 목적

- 예약 상세 조회 API의 응답 시간 및 처리량 측정
- JWT 인증 및 예약 상세 응답 데이터 검증

### 대상 API

`GET /api/reservations/{reservationId}`

### 테스트 데이터 준비

- 공통 테스트 회원 1번 계정 사용
- 해당 회원의 확정된 예약 데이터 1건 생성
- 실제 예약 ID를 환경변수에 설정

```dotenv
RESERVATION_ID=234
```

※ 234는 로컬 테스트에서 사용한 ID이며, 배포 환경에서는 실제 생성된 예약 ID로 변경해야 한다.

### 실행 방법

로컬 Smoke Test:

```bash
k6 run -e SMOKE=true reservation/reservation-read-test.js
```

배포 환경 부하 테스트:

```bash
k6 run -e SMOKE=false reservation/reservation-read-test.js
```

※ k6 디렉토리 기준이며, 실행 전 환경변수 및 테스트 서버 주소 설정이 필요하다.

### 로컬 테스트 결과

| 항목 | 결과 |
|---|---:|
| 가상 사용자 | 1명 |
| 테스트 시간 | 20초 |
| 예약 조회 요청 | 20건 |
| 평균 응답 시간 | 17.94ms |
| p95 응답 시간 | 20.36ms |
| HTTP 실패율 | 0% |
| 응답 검증 성공률 | 100% (80/80) |
| Threshold | PASS |

### 배포 환경 테스트 계획

| 항목 | 설정 |
|---|---|
| 테스트 회원 | 1명 |
| 예약 데이터 | 1건 |
| 가상 사용자 | 10 → 30 → 50 → 100명 |
| 테스트 시간 | 6분 30초 |
| 요청 간 대기 시간 | 1초 |
| p95 목표 | 1,000ms 미만 |
| HTTP 실패율 | 1% 미만 |
| 응답 검증 성공률 | 99% 초과 |

현재 스크립트는 동일 회원의 동일 예약 데이터를 반복 조회하는 방식이다. 실제 다중 회원 조회 부하 테스트가 필요한 경우 회원별 예약 데이터와 JWT를 별도로 준비해야 한다.

배포 환경 테스트 결과는 실행 후 추가 기록한다.

<hr>

## 2. 동시 예약 정원 초과 방지 테스트

### 테스트 목적

다수의 회원이 동일 회차에 동시에 예약을 요청할 때 정원을 초과하여 예약되는 것을 방지하는지 검증한다.

### 대상 API

`POST /api/reservations`

### 테스트 데이터 준비

- 서로 다른 테스트 회원 10명
- 예약 가능한 OPEN 상태의 회차 1개
- 초기 잔여 정원 5명
- 해당 회차에 테스트 회원의 기존 활성 예약이 없어야 함

### 환경변수

```dotenv
TIME_SLOT_ID=63
VUS=10
AVAILABLE_CAPACITY=5
```

회차 ID는 테스트 환경의 실제 데이터에 맞게 변경한다.

### 실행 방법

```bash
k6 run reservation/reservation-concurrency-test.js
```

### 로컬 테스트 결과

| 항목 |     결과 |
|---|-------:|
| 가상 사용자 |    10명 |
| 초기 잔여 정원 |     5명 |
| 예약 요청 |    10건 |
| 예약 성공 |     5건 |
| 정원 초과 거절 |     5건 |
| 예상하지 못한 오류 |     0건 |
| 응답 검증 성공률 |   100% |
| 전체 실행 시간 | 약 1.6초 |
| Threshold |   PASS |
| DB 데이터 정합성 |     확인 |

### DB 데이터 정합성 검증

| 검증 항목 | 예상 결과 | 실제 결과 |
|---|---:|---:|
| 생성된 예약 건수 | 5건 | 5건 |
| 예약 인원 합계 | 5명 | 5명 |
| 서로 다른 예약 회원 | 5명 | 5명 |
| HOLD 상태 예약 | 5건 | 5건 |
| 정원 초과 예약 | 0건 | 0건 |

동일 회차에 서로 다른 회원 10명이 동시에 예약을 요청했을 때 잔여 정원에 해당하는 5건만 성공하고, 나머지 5건은 CAPACITY_EXCEEDED로 정상 거절되었다.

모든 k6 Threshold를 통과했으며 DB에서도 예약 5건이 정상적으로 생성된 것을 확인하였다.

HTTP 409(CAPACITY_EXCEEDED)는 예상된 비즈니스 예외이므로 정상 거절로 처리한다.

### 배포 환경 테스트 계획

| 단계 | VU | 잔여 정원 | 예상 성공 | 예상 거절 |
|---|---:|---:|---:|---:|
| 1차 | 10 | 5 | 5 | 5 |
| 2차 | 30 | 15 | 15 | 15 |
| 3차 | 50 | 25 | 25 | 25 |
| 4차 | 100 | 50 | 50 | 50 |

```bash
# 10명
k6 run -e VUS=10 -e AVAILABLE_CAPACITY=5 reservation/reservation-concurrency-test.js
# 30명
k6 run -e VUS=30 -e AVAILABLE_CAPACITY=15 reservation/reservation-concurrency-test.js
# 50명
k6 run -e VUS=50 -e AVAILABLE_CAPACITY=25 reservation/reservation-concurrency-test.js
# 100명
k6 run -e VUS=100 -e AVAILABLE_CAPACITY=50 reservation/reservation-concurrency-test.js
```
각 단계는 독립적으로 실행하며 테스트 전 잔여 정원과 기존 활성 예약 여부를 확인한다.

최대 100명의 테스트 회원을 준비한다.

테스트 종료 직후 DB의 RESERVED_COUNT, 활성 예약 건수 및 예약 인원 합계를 검증한다.

HOLD 예약은 10분 후 만료되므로 만료 처리 전에 결과를 확인해야 한다.

배포 환경에서는 응답 시간, 서버 자원 사용량, DB 커넥션 풀 및 락 경합 등을 추가로 확인한다.

<hr>

## 3. 동일 회원 중복 예약 방지 테스트

### 테스트 목적

동일 회원이 동일 회차에 여러 예약 요청을 동시에 전송했을 때 중복 예약이 생성되지 않는지 검증한다.

### 대상 API

`POST /api/reservations`

### 테스트 데이터 준비

- 테스트 회원 1번 계정 사용
- 예약 가능한 OPEN 상태의 회차 1개
- 초기 잔여 정원 20명
- 해당 회원의 기존 활성 예약이 없어야 함

### 환경변수

```dotenv
DUPLICATE_TIME_SLOT_ID=64
VUS=10
```

회차 ID는 테스트 환경의 실제 데이터에 맞게 변경한다.

### 실행 방법

```bash
k6 run reservation/reservation-duplicate-test.js
```

### 로컬 테스트 결과

| 항목 | 결과 |
|---|---:|
| 테스트 회원 | 1명 |
| 가상 사용자 | 10명 |
| 예약 요청 | 10건 |
| 예약 성공 | 1건 |
| 중복 예약 거절 | 9건 |
| 예상하지 못한 오류 | 0건 |
| 응답 검증 성공률 | 100% |
| 전체 실행 시간 | 약 0.2초 |
| Threshold | PASS |
| DB 데이터 정합성 | PASS |

HTTP 409(DUPLICATE_RESERVATION)는 예상된 비즈니스 예외이므로 정상 거절로 처리한다.

### 배포 환경 테스트 계획

| 단계 | VU | 테스트 회원 | 예상 성공 | 예상 거절 |
|---|---:|---:|---:|---:|
| 1차 | 10 | 1명 | 1건 | 9건 |
| 2차 | 30 | 1명 | 1건 | 29건 |
| 3차 | 50 | 1명 | 1건 | 49건 |
| 4차 | 100 | 1명 | 1건 | 99건 |

### DB 데이터 정합성 검증

| 검증 항목 | 예상 결과 | 실제 결과 |
|---|---:|---:|
| 생성된 예약 건수 | 1건 | 1건 |
| 예약 인원 합계 | 1명 | 1명 |
| HOLD 상태 예약 | 1건 | 1건 |
| RESERVED_COUNT | 1명 | 1명 |
| 중복 예약 생성 | 0건 | 0건 |

동일 회원이 동일 회차에 예약 요청 10건을 전송했을 때 1건만 성공하고, 나머지 9건은 DUPLICATE_RESERVATION으로 정상 거절되었다.

모든 k6 Threshold를 통과했으며 DB에서도 예약이 1건만 생성되고 회차의 예약 인원이 정확하게 증가한 것을 확인하였다.

### 배포 환경 실행 예시

```bash
k6 run -e VUS=10 reservation/reservation-duplicate-test.js

k6 run -e VUS=30 reservation/reservation-duplicate-test.js

k6 run -e VUS=50 reservation/reservation-duplicate-test.js

k6 run -e VUS=100 reservation/reservation-duplicate-test.js
```

각 단계는 독립적으로 실행하며, 실행 전 기존 활성 예약이 없는지 확인한다.

테스트 종료 후 동일 회원의 활성 예약이 1건만 존재하고 RESERVED_COUNT가 1만큼 증가했는지 확인한다.