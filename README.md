<img width="300" height="200" alt="image" src="https://github.com/user-attachments/assets/acf0b59d-3691-4cda-ad39-6b4a9f451a79" />

## 프로젝트 소개
<p align="center">   <img     width="620"     alt="POPQ 프로젝트 소개"     src="https://github.com/user-attachments/assets/281c0d53-cefa-4211-a646-dd168d28cad9"   /> </p>

팝업스토어 예약부터 굿즈 주문/결제까지 한 번에 이용할 수 있는 팝업스토어 통합 플랫폼입니다.

기존에는 팝업스토어의 운영 기간, 위치, 예약 방법, 굿즈 정보가 여러 채널에 흩어져 있어 사용자가 필요한 정보를 한 번에 확인하기 어렵고, 예약과 웨이팅, 상품 구매 역시 각각 다른 서비스에서 이루어지는 경우가 많았습니다.

POPQ는 이러한 불편을 줄이기 위해 팝업스토어 정보 조회, 사전 예약, 현장 웨이팅, 입장 관리, 굿즈 구매 기능을 하나의 플랫폼으로 통합했습니다.

특히 팝업 종료 후에도 관련 한정 굿즈를 온라인으로 구매할 수 있도록 구성해, 오프라인 방문 경험이 이후의 구매 경험으로 이어질 수 있도록 한 것이 특징입니다.

> **PROJECT INFO**
> 
> **개발 기간** : 2026.10.22 ~ 2026.12.10  
> **개발 인원** : 6명 · Frontend 2명 · Backend 4명  
> **배포 환경** : AWS

<br>

## 핵심 과제

POPQ는 팝업스토어 이용 과정에서 발생하는 여러 단절 지점을 하나의 서비스 흐름으로 연결하는 데 초점을 두었습니다.

### 01. 팝업 정보와 이용 과정의 분산

**Challenge**  
팝업 일정, 위치, 예약, 웨이팅, 굿즈 정보가 여러 채널에 흩어져 있어 사용자가 방문 전부터 구매까지의 정보를 한 곳에서 확인하기 어려웠습니다.

**Solution**  
`팝업 탐색 → 상세 조회 → 예약/웨이팅 → 현장 방문 → 굿즈 구매`가 하나의 서비스 안에서 이어지도록 구성했습니다.

---

### 02. 예약과 현장 운영의 정합성

**Challenge**  
동일한 시간대에 예약 요청이 동시에 발생하거나, 예약 후 절차를 완료하지 않는 경우 예약 가능 인원과 실제 운영 상태가 달라질 수 있습니다.

**Solution**  
비관적 락을 적용해 동시 예약 요청을 제어하고, Redis TTL 기반 10분 타이머로 예약 유효 시간을 관리했습니다.

---

### 03. 팝업 종료 이후의 구매 경험

**Challenge**  
팝업이 종료되면 현장에서 놓친 한정 굿즈를 다시 구매하기 어렵고, 오프라인 경험이 종료와 동시에 단절되는 문제가 있습니다.

**Solution**  
팝업 종료 이후 관련 굿즈가 온라인 판매로 이어지도록 팝업 운영과 상품 판매 라이프사이클을 연결했습니다.

---

### 04. 주문 · 결제 · 재고 상태 관리

**Challenge**  
미결제 주문, 결제 실패 및 취소 등의 상황에서 주문 · 결제 · 재고 상태가 서로 달라질 수 있습니다.

**Solution**  
Redis TTL로 미결제 주문을 10분 동안 관리하고, Toss Payments 결제 승인 흐름과 주문 · 결제 · 재고 변경을 연계했습니다.

---

### 05. 운영 주체별 관리 범위

**Challenge**  
일반 사용자, 팝업 운영자, 전체 관리자가 필요로 하는 기능과 접근할 수 있는 데이터 범위가 서로 다릅니다.

**Solution**  
Spring Security 기반으로 `USER · MANAGER · ADMIN` 역할을 구분하고 역할별 API 및 관리 기능의 접근 범위를 분리했습니다.

<br>

<details>
<summary><b>구현 포인트 한눈에 보기</b></summary>
<br>

- **Popup** — 팝업 탐색, 상세 조회, 운영 상태 관리
- **Reservation** — 비관적 락 기반 동시성 제어, Redis TTL 10분 예약 관리
- **Waiting** — 현장 웨이팅 및 입장 상태 관리
- **Commerce** — 팝업 종료 후 온라인 굿즈 판매
- **Order** — Redis TTL 10분 미결제 주문 관리, 재고 및 주문 상태 관리
- **Payment** — Toss Payments 승인/취소 및 주문·결제 상태 연계
- **Admin** — 역할별 팝업 운영, 주문 및 매출 관리

</details>

<br>

## 팀원 역할

<br>

## 기술 스택

### Backend
![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Oracle](https://img.shields.io/badge/Oracle_Database-F80000?style=for-the-badge&logo=oracle&logoColor=white)
![JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-D92C20?style=for-the-badge&logo=redis&logoColor=white)

### Frontend
![React](https://img.shields.io/badge/React-222222?style=for-the-badge&logo=react&logoColor=61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white)

### Infrastructure
![AWS](https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazonaws&logoColor=white)
![Docker](https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)

### External Service
![Toss Payments](https://img.shields.io/badge/Toss_Payments-0064FF?style=for-the-badge&logoColor=white)

<br>

## ERD

<br>

## API 명세

<br>

## 성능 테스트

<br>

## 폴더 구조

<br>

## 실행 환경

| 구분 | 환경 |
| --- | --- |
| Java | JDK 21 |
| Backend | Spring Boot 4.1.1 |
| Build Tool | Maven |
| Database | Oracle Database |
| Cache / TTL | Redis |
| Frontend | React + TypeScript + Vite |
| Package Manager | npm |
| API Documentation | Swagger / OpenAPI |

## 실행 방법 및 환경 설정

### 사전 요구사항

프로젝트 실행을 위해 아래 환경이 필요합니다.

- JDK 21
- Node.js 20+
- npm
- Oracle Database
- Redis
- Maven

### 환경 변수 설정

Backend 실행에 필요한 민감 정보는 `.env` 파일에서 관리합니다.

프로젝트에 포함된 `.env.example`을 복사하여 `.env` 파일을 생성합니다.

```bash
cp .env.example .env
```

`.env`에는 다음과 같은 값을 설정합니다.

```env
DB_URL=
DB_USERNAME=
DB_PASSWORD=

REDIS_HOST=
REDIS_PORT=

JWT_SECRET=

TOSS_SECRET_KEY=
```

> 실제 DB 계정, JWT Secret, Toss Payments Secret Key 등의 민감 정보는 Git 저장소에 포함하지 않습니다.

### 인프라 실행

Backend 실행 전 Oracle Database와 Redis가 실행되어 있어야 합니다.

```text
Oracle Database
Redis
```

### Backend 실행

```bash
cd backend
./mvnw spring-boot:run
```

실행 후 Backend API는 아래 주소에서 확인할 수 있습니다.

```text
http://localhost:8080
```

### Frontend 실행

```bash
cd frontend
npm install
npm run dev
```

실행 후 아래 주소로 접속합니다.

```text
http://localhost:5173
```

### 배포

> 추후 배포 환경 구성 후 추가 예정
