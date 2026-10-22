
import { useEffect, useState } from "react";

function ReservationTimer({ selectedDate, selectedTime }) {
  const getRemainingTime = () => {
    if (!selectedDate || !selectedTime) return 0;

    const reservationDateTime = new Date(
      `${selectedDate}T${selectedTime}:00`
    );

    return reservationDateTime.getTime() - Date.now();
  };

  const [remainingTime, setRemainingTime] = useState(getRemainingTime);

  useEffect(() => {
    const timer = setInterval(() => {
      setRemainingTime(getRemainingTime());
    }, 1000);

    return () => clearInterval(timer);
  }, [selectedDate, selectedTime]);

  const formatTime = (milliseconds) => {
    const totalSeconds = Math.floor(milliseconds / 1000);

    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    return `${days}일 ${String(hours).padStart(2, "0")}:${String(
      minutes
    ).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
  };

  return (
    <div className="reservation-timer">
      {remainingTime > 0 ? (
        <>
          <p>예약 시간까지 남은 시간</p>
          <h2>{formatTime(remainingTime)}</h2>
        </>
      ) : (
        <>
          <h2>예약 시간이 되었습니다!</h2>
          <p>예약 시간을 확인하고 방문해 주세요.</p>
        </>
      )}
    </div>
  );
}

export default ReservationTimer;
