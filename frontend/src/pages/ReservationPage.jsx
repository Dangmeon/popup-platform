
import { useState } from "react";
import ReservationCalendar from "../components/reservation/ReservationCalendar";
import TimeSlotSelector from "../components/reservation/TimeSlotSelector";
import "./ReservationPage.css";

function ReservationPage({ onNext }) {
  const [selectedDate, setSelectedDate] = useState(null);
  const [selectedTime, setSelectedTime] = useState(null);

  // 날짜 선택
  const handleDateSelect = (date) => {
    setSelectedDate(date);
    setSelectedTime(null);
  };

  // 시간 선택
  const handleTimeSelect = (time) => {
    setSelectedTime(time);
  };

  return (
    <main className="reservation-page">
      {/* 예약 페이지 상단 */}
      <div className="reservation-hero">
        <div className="reservation-brand">
          <span className="reservation-logo">
            POP<span>Q</span>
          </span>
          <span className="reservation-brand-tag">POP-UP EXPERIENCE</span>
        </div>

        <h1>설레는 팝업, 지금 예약하세요!</h1>
        <p>방문하고 싶은 날짜와 시간을 선택해 주세요.</p>
      </div>

      {/* 예약 진행 단계 */}
      <div className="reservation-steps">
        <div className="reservation-step active">
          <span>01</span>
          날짜 · 시간 선택
        </div>
        <span className="reservation-step-arrow">→</span>
        <div className="reservation-step">
          <span>02</span>
          예약 확인
        </div>
        <span className="reservation-step-arrow">→</span>
        <div className="reservation-step">
          <span>03</span>
          예약 완료
        </div>
      </div>

      {/* 달력 */}
      <ReservationCalendar
        selectedDate={selectedDate}
        onDateSelect={handleDateSelect}
      />

      {/* 시간 선택 */}
      <TimeSlotSelector
        selectedDate={selectedDate}
        selectedTime={selectedTime}
        onTimeSelect={handleTimeSelect}
      />

      {/* 예약 정보 */}
      {selectedDate && selectedTime && (
        <div className="reservation-summary">
          <div className="reservation-summary-header">
            <h3>선택한 예약 정보</h3>
            <span className="reservation-summary-badge">선택 완료</span>
          </div>

          <div className="reservation-summary-row">
            <span>예약 날짜</span>
            <strong>{selectedDate}</strong>
          </div>

          <div className="reservation-summary-row">
            <span>예약 시간</span>
            <strong>{selectedTime}</strong>
          </div>

          <button
            type="button"
            className="reservation-next-button"
            onClick={() => onNext(selectedDate, selectedTime)}
          >
            예약 정보 확인하기 →
          </button>
        </div>
      )}
    </main>
  );
}

export default ReservationPage;
