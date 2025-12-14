package com.doublez.kc_forum.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArticleViewEvent implements Serializable {
    private Long articleId;
    private Long timestamp;
}
