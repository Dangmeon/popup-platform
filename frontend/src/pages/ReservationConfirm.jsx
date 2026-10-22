
import { QRCodeSVG } from "qrcode.react";
import "./ReservationComplete.css";

function ReservationComplete({ selectedDate, selectedTime }) {
  // 백엔드 연결 전까지 사용하는 테스트 예약 번호
  const reservationNumber = "DEMO-000001";

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

        <h1>예약 확인증</h1>
        <p>방문 시 직원에게 예약 확인증을 보여주세요.</p>
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

      {/* 예약 정보 */}
      <section className="reservation-summary">
        <div className="reservation-summary-header">
          <h3>예약 정보</h3>
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
          <strong>{reservationNumber}</strong>
        </div>

        {/* QR코드 */}
        <div className="reservation-qr-section">
          <div className="reservation-qr-placeholder">
            <QRCodeSVG
              value={reservationNumber}
              size={160}
              marginSize={4}
            />
          </div>

          <p>
            방문 시 QR코드와 예약 번호를
            직원에게 보여주세요.
          </p>
        </div>

        {/* 데모 안내 */}
        <p className="reservation-confirm-notice">
          ※ 현재는 프론트엔드 테스트용 화면입니다.
          실제 예약은 생성되지 않았으며,
          QR코드는 입장에 사용할 수 없습니다.
        </p>
      </section>
    </main>
  );
}

export default ReservationComplete;
