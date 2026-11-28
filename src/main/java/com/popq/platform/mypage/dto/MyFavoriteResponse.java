package com.popq.platform.mypage.dto;

import com.popq.platform.favorite.entity.Favorite;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MyFavoriteResponse {

    private Long favoriteId;
    private Long popId;
    private String popTitle;
    private String imageUrl;
    private LocalDateTime createdAt;

    public static MyFavoriteResponse from(Favorite favorite) {
        return MyFavoriteResponse.builder()
                .favoriteId(favorite.getFavoriteId())
                .popId(favorite.getPopupStore().getPopId())
                .popTitle(favorite.getPopupStore().getTitle())
                .imageUrl(favorite.getPopupStore().getStoreImg())
                .createdAt(favorite.getCreatedAt())
                .build();
    }
}