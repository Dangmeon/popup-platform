package com.popq.platform.controller;

import com.popq.platform.dto.TimeSlotGenerateRequest;
import com.popq.platform.dto.TimeSlotRequest;
import com.popq.platform.dto.TimeSlotResponse;
import com.popq.platform.service.TimeSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TimeSlotController {
    TimeSlotService service;

    @Autowired
    public TimeSlotController(TimeSlotService service) {
        this.service = service;
    }

    // 팝업의 회차 목록 조회
    // /api/popups/1/slots                  : 1번 팝업의 전체 회차
    // /api/popups/1/slots?date=2026-11-01  : 1번 팝업의 11월 1일 회차
    @GetMapping("/api/popups/{popId}/slots")
    public List<TimeSlotResponse> listTimeSlot(
            @PathVariable("popId") Long popId,
            @RequestParam(value = "date", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return service.listTimeSlot(popId, date);
    }

    // 회차 상세 조회
    @GetMapping("/api/slots/{timeSlotId}")
    public TimeSlotResponse detailTimeSlot(@PathVariable("timeSlotId") Long timeSlotId) {
        return service.detailTimeSlot(timeSlotId);
    }

    // 아래 등록/생성/수정/삭제는 /api/admin/** 이라 SecurityConfig에서 ADMIN만 호출 가능

    // 회차 1개 등록 : popId는 URL에서 받음
    @PostMapping("/api/admin/popups/{popId}/slots")
    public TimeSlotResponse insertTimeSlot(@PathVariable("popId") Long popId, @RequestBody TimeSlotRequest request) {
        return service.insertTimeSlot(popId, request); // 등록된 회차 정보 반환
    }

    // 회차 자동 생성 (예약 시간을 간격대로 나눠서 날짜별로 생성, 점심시간 제외)
    @PostMapping("/api/admin/popups/{popId}/slots/generate")
    public Map<String, Integer> generateTimeSlots(@PathVariable("popId") Long popId, @RequestBody TimeSlotGenerateRequest request) {
        Map<String, Integer> result = new HashMap<String, Integer>();
        result.put("createdCount", service.generateTimeSlots(popId, request)); // {"createdCount": 생성된 개수}
        return result;
    }

    // 회차 수정 (예약이 있으면 정원만 변경 가능)
    @PutMapping("/api/admin/slots/{timeSlotId}")
    public void updateTimeSlot(@PathVariable("timeSlotId") Long timeSlotId, @RequestBody TimeSlotRequest request) {
        service.updateTimeSlot(timeSlotId, request);
    }

    // 회차 삭제 (예약이나 예약 이력이 있으면 삭제 불가)
    @DeleteMapping("/api/admin/slots/{timeSlotId}")
    public void deleteTimeSlot(@PathVariable("timeSlotId") Long timeSlotId) {
        service.deleteTimeSlot(timeSlotId);
    }
}