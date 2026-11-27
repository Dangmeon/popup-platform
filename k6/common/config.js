// 테스트 대상 백엔드 서버 주소
// BASE_URL 환경변수가 없으면 로컬 서버 사용
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// HTTP 요청에 공통으로 사용하는 설정
export function getHeaders(token, phase = 'load') {
    return {
        headers: {
            // JSON 형식으로 요청 데이터 전송
            'Content-Type': 'application/json',

            // JWT 토큰이 있는 경우에만 인증 헤더 추가
            ...(token
                ? { Authorization: `Bearer ${token}` }
                : {}),
        },

        // 테스트 준비 요청과 실제 부하 요청을 구분
        tags: {
            phase: phase,
        },

        // HTTP 요청 최대 대기 시간
        timeout: '10s',
    };
}

// 상품 ID, 예약 ID 등의 필수 환경변수 검증
export function getRequiredId(name) {

    const value = Number(__ENV[name]);

    // 양의 정수가 아니면 테스트 실행 중단
    if (!Number.isSafeInteger(value) || value <= 0) {
        throw new Error(`${name} 설정을 확인해 주세요.`);
    }

    return value;
}

// JSON 응답 데이터를 안전하게 반환
export function getBody(response) {

    try {
        return response.json();
    } catch {
        // JSON 형식이 아닌 응답은 null 반환
        return null;
    }
}