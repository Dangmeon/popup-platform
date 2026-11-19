package com.popq.platform.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 팝업 담당 매니저 배정 요청 : 매니저 이메일 (비우면 배정 해제)
@Getter
@Setter
@NoArgsConstructor
public class PopupManagerRequest {
    private String managerEmail;
}
