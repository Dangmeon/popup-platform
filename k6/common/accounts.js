// 테스트 회원 정보 생성
// index: 테스트 회원 번호
export function getAccount(index) {

    // 회원 번호 유효성 검증
    if (!Number.isInteger(index) || index <= 0 || index > 9999) {
        throw new Error('테스트 회원 번호 설정 오류');
    }

    // 회원 번호를 네 자리 문자열로 변환
    // 예: 1 -> 0001, 10 -> 0010
    const number = String(index).padStart(4, '0');

    // 테스트 회원 비밀번호는 환경변수에서 가져옴
    // 비밀번호가 GitHub에 노출되지 않도록 관리
    const password = __ENV.TEST_PASSWORD;

    if (!password) {
        throw new Error('TEST_PASSWORD 설정이 필요합니다.');
    }

    // SignupRequest의 비밀번호 정책 확인
    // 영문, 숫자, 특수문자 포함 8~16자
    const passwordPattern =
        /^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,16}$/;

    if (!passwordPattern.test(password)) {
        throw new Error('테스트 비밀번호가 회원가입 조건에 맞지 않습니다.');
    }

    // SignupRequest DTO에 맞춰 회원 정보 생성
    return {
        username: `popq.k6.test.${number}@gmail.com`,
        password: password,
        name: `테스트회원${index}`,
        phone: `0109999${number}`,
    };
}