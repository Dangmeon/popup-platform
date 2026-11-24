package com.popq.platform.favorite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class FavoriteResponse {
    private Long favoriteId;
    private Long popId;
    private Boolean isFavorite;

    public static FavoriteResponse of(Long favoriteId, Long popId, boolean isFavorite) {
        return FavoriteResponse.builder()
                .favoriteId(favoriteId)
                .popId(popId)
                .isFavorite(isFavorite)
                .build();
    }
}