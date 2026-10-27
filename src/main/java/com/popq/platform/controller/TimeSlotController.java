package com.popq.platform.controller;

import com.popq.platform.dto.TimeSlotGenerateRequest;
import com.popq.platform.dto.TimeSlotRequest;
import com.popq.platform.dto.TimeSlotResponse;
import com.popq.platform.service.TimeSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@CrossOrigin("*")
@RestController
public class TimeSlotController {
    TimeSlotService service;

    @Autowired
    public TimeSlotController(TimeSlotService service) {
        this.service = service;
    }

    // 팝업의 회차 목록 조회
    // /timeslot/list/1                  : 1번 팝업의 전체 회차
    // /timeslot/list/1?date=2026-11-01  : 1번 팝업의 11월 1일 회차
    @GetMapping("/timeslot/list/{popId}")
    public List<TimeSlotResponse> listTimeSlot(
            @PathVariable("popId") Long popId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return service.listTimeSlot(popId, date);
    }

    // 회차 상세 조회
    @GetMapping("/timeslot/detail/{timeSlotId}")
    public TimeSlotResponse detailTimeSlot(@PathVariable("timeSlotId") Long timeSlotId) {
        return service.detailTimeSlot(timeSlotId);
    }

    // [회원 연동] 아래 등록/생성/수정/삭제는 관리자(ADMIN)만 호출 가능하도록 권한 체크 필요
    //  - Security/ADMIN 권한 작업 후 적용

    // 회차 1개 등록
    @PostMapping("/timeslot/insert")
    public Long insertTimeSlot(@RequestBody TimeSlotRequest request) {
        return service.insertTimeSlot(request); // 생성된 timeSlotId 반환
    }

    // 회차 자동 생성 (예약 시간을 간격대로 나눠서 날짜별로 생성, 점심시간 제외)
    @PostMapping("/timeslot/generate")
    public int generateTimeSlots(@RequestBody TimeSlotGenerateRequest request) {
        return service.generateTimeSlots(request); // 생성된 회차 개수 반환
    }

    // 회차 수정
    @PutMapping("/timeslot/update/{timeSlotId}")
    public void updateTimeSlot(@PathVariable("timeSlotId") Long timeSlotId, @RequestBody TimeSlotRequest request) {
        service.updateTimeSlot(timeSlotId, request);
    }

    // 회차 삭제
    @DeleteMapping("/timeslot/delete/{timeSlotId}")
    public void deleteTimeSlot(@PathVariable("timeSlotId") Long timeSlotId) {
        service.deleteTimeSlot(timeSlotId);
    }

    // 서비스에서 IllegalArgumentException 발생 시 400 + 메시지 반환
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
