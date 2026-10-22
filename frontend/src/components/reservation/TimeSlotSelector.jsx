
import "./TimeSlotSelector.css";

function TimeSlotSelector({ selectedDate, selectedTime, onTimeSelect }) {
  // 임시 예약 시간 데이터
  const timeSlots = [
    { time: "10:00", remaining: 12 },
    { time: "11:00", remaining: 8 },
    { time: "12:00", remaining: 5 },
    { time: "13:00", remaining: 18 },
    { time: "14:00", remaining: 0 },
    { time: "15:00", remaining: 10 },
    { time: "16:00", remaining: 7 },
    { time: "17:00", remaining: 3 },
  ];

  return (
    <div className="time-slot-selector">
      <h3>예약 시간 선택</h3>

      {!selectedDate ? (
        <p className="time-slot-notice">
          먼저 예약 날짜를 선택해주세요.
        </p>
      ) : (
        <>
          {/* 예약 시간 */}
          <div className="time-slot-grid">
            {timeSlots.map((slot) => (
              <button
                type="button"
                key={slot.time}
                disabled={slot.remaining === 0}
                className={`time-slot ${
                  selectedTime === slot.time ? "selected" : ""
                }`}
                onClick={() => onTimeSelect(slot.time)}
              >
                <span className="time-slot-time">{slot.time}</span>

                <span className="time-slot-remaining">
                  {slot.remaining === 0
                    ? "예약 마감"
                    : `잔여 ${slot.remaining}명`}
                </span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

export default TimeSlotSelector;
