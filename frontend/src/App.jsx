import { useState } from "react";
import ReservationPage from "./pages/ReservationPage";
import ReservationConfirm from "./pages/ReservationConfirm";
import ReservationComplete from "./pages/ReservationComplete";

function App() {
  const [step, setStep] = useState("select");
  const [reservation, setReservation] = useState(null);

  const handleNext = (selectedDate, selectedTime) => {
    setReservation({ selectedDate, selectedTime });
    setStep("confirm");
  };

  return (
    <div>
      {step === "select" && (
        <ReservationPage onNext={handleNext} />
      )}

      {step === "confirm" && reservation && (
        <ReservationConfirm
          selectedDate={reservation.selectedDate}
          selectedTime={reservation.selectedTime}
          onBack={() => setStep("select")}
          onConfirm={() => setStep("complete")}
        />
      )}

      {step === "complete" && reservation && (
        <ReservationComplete
          selectedDate={reservation.selectedDate}
          selectedTime={reservation.selectedTime}
        />
      )}
    </div>
  );
}

export default App;