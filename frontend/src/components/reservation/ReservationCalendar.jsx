
import { useState } from "react";
import "./ReservationCalendar.css";

function ReservationCalendar({ selectedDate, onDateSelect }) {
  const [currentMonth, setCurrentMonth] = useState(new Date());

  const year = currentMonth.getFullYear();
  const month = currentMonth.getMonth();

  // 예약 가능 기간
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const maxDate = new Date(today);
  maxDate.setDate(today.getDate() + 13);

  // 달력 날짜 생성
  const firstDay = new Date(year, month, 1).getDay();
  const lastDate = new Date(year, month + 1, 0).getDate();

  const dates = Array.from(
    { length: firstDay + lastDate },
    (_, index) => (index < firstDay ? null : index - firstDay + 1)
  );

  // 날짜 선택
  const handleDateClick = (day) => {
    const formattedDate = [
      year,
      String(month + 1).padStart(2, "0"),
      String(day).padStart(2, "0"),
    ].join("-");

    onDateSelect(formattedDate);
  };

  // 예약 가능 여부
  const isAvailable = (day) => {
    const date = new Date(year, month, day);
    return date >= today && date <= maxDate;
  };

  // 월 이동
  const moveMonth = (amount) => {
    setCurrentMonth(new Date(year, month + amount, 1));
  };

  const previousMonth = new Date(year, month - 1, 1);
  const nextMonth = new Date(year, month + 1, 1);

  const canMovePrevious =
    previousMonth.getFullYear() > today.getFullYear() ||
    (previousMonth.getFullYear() === today.getFullYear() &&
      previousMonth.getMonth() >= today.getMonth());

  const canMoveNext =
    nextMonth.getFullYear() < maxDate.getFullYear() ||
    (nextMonth.getFullYear() === maxDate.getFullYear() &&
      nextMonth.getMonth() <= maxDate.getMonth());

  return (
    <div className="reservation-calendar">
      {/* 달력 헤더 */}
      <div className="calendar-header">
        <h3>예약 날짜 선택</h3>

        <div className="calendar-navigation">
          <button
            type="button"
            onClick={() => moveMonth(-1)}
            disabled={!canMovePrevious}
            aria-label="이전 달"
          >
            ‹
          </button>

          <span>
            {year}년 {month + 1}월
          </span>

          <button
            type="button"
            onClick={() => moveMonth(1)}
            disabled={!canMoveNext}
            aria-label="다음 달"
          >
            ›
          </button>
        </div>
      </div>

      {/* 달력 */}
      <div className="calendar-grid">
        {["일", "월", "화", "수", "목", "금", "토"].map((day) => (
          <div className="calendar-weekday" key={day}>
            {day}
          </div>
        ))}

        {dates.map((day, index) => {
          if (day === null) {
            return <div key={`empty-${index}`} />;
          }

          const dateString = [
            year,
            String(month + 1).padStart(2, "0"),
            String(day).padStart(2, "0"),
          ].join("-");

          const available = isAvailable(day);
          const selected = selectedDate === dateString;

          return (
            <button
              type="button"
              key={dateString}
              disabled={!available}
              aria-pressed={selected}
              className={`calendar-day ${
                selected ? "selected" : ""
              } ${available ? "" : "unavailable"}`}
              onClick={() => handleDateClick(day)}
            >
              {day}
            </button>
          );
        })}
      </div>

      {/* 예약 안내 */}
      <p className="calendar-notice">
        예약은 오늘부터 14일 이내의 날짜만 선택할 수 있습니다.
      </p>
    </div>
  );
}

export default ReservationCalendar;
