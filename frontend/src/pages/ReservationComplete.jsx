import "./ReservationComplete.css";

function ReservationComplete({ selectedDate, selectedTime }) {
  return (
    <main className="reservation-page">
      {/* POPQ 로고 */}
      <div className="reservation-hero">
        <div className="reservation-brand">
          <span className="reservation-logo">
            POP<span>Q</span>
          </span>
          <span className="reservation-brand-tag">
            POP-UP EXPERIENCE
          </span>
        </div>

        <h1>예약이 완료되었습니다!</h1>
        <p>방문 당일 QR코드를 제시해 주세요.</p>
      </div>

      {/* 예약 진행 단계 */}
      <div className="reservation-steps">
        <div className="reservation-step">
          <span>01</span>날짜 · 시간 선택
        </div>

        <span className="reservation-step-arrow">→</span>

        <div className="reservation-step">
          <span>02</span>예약 확인
        </div>

        <span className="reservation-step-arrow">→</span>

        <div className="reservation-step active">
          <span>03</span>예약 완료
        </div>
      </div>

      {/* 예약 완료 정보 */}
      <section className="reservation-summary">
        <div className="reservation-summary-header">
          <h3>예약 완료 정보</h3>
          <span className="reservation-summary-badge">
            데모 화면
          </span>
        </div>

        <div className="reservation-summary-row">
          <span>예약 날짜</span>
          <strong>{selectedDate}</strong>
        </div>

        <div className="reservation-summary-row">
          <span>예약 시간</span>
          <strong>{selectedTime}</strong>
        </div>

        <div className="reservation-summary-row">
          <span>예약 번호</span>
          <strong>DEMO-000001</strong>
        </div>

        {/* QR코드 영역 */}
        <div className="reservation-qr-section">
          <div className="reservation-qr-placeholder">
            QR코드 표시 영역
          </div>

          <p>실제 QR코드는 예약 API 연동 후 발급됩니다.</p>
        </div>

        <p className="reservation-confirm-notice">
          ※ 현재 화면은 프론트엔드 테스트용입니다.
          실제 예약은 생성되지 않았습니다.
        </p>
      </section>
    </main>
  );
}

export default ReservationComplete;