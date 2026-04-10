package com.jojo.prompt.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromptFavoriteListItem {
    private Long favoriteId;
    private LocalDateTime favoriteTime;

    private Long promptId;
    private String title;
    private String content;
    private String tags;
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer copyCount;
    private Long userId;
    private Long categoryId;
    private String categoryName;
}