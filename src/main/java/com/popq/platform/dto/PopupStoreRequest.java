package com.popq.platform.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class PopupStoreRequest {
    private String title;
    private String location;
    private LocalDate startDate;
    private LocalDate endDate;
    private String category;
    private String description;
    private String storeImg;
    private Integer roundNum;
    private String reservStart;
    private String reservEnd;
    private Integer reservPeopleCap;
    private String walkinStatus;
}
