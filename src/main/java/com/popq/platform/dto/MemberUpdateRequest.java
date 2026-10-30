package com.popq.platform.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberUpdateRequest {
    private String password;
    private String name;
    private String phone;
}