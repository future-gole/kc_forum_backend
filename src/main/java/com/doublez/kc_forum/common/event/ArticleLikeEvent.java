package com.doublez.kc_forum.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticleLikeEvent implements Serializable {
    private Long userId;
    private Long targetId;
    private String targetType;
    private Long timestamp;
    private boolean isLiked; // true for like, false for unlike
}
