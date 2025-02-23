package com.doublez.kc_forum.service;

import org.springframework.transaction.annotation.Transactional;

public interface ILikesService {
    /**
     * 点赞
     * @param userId
     * @param targetId
     * @param targetType
     */
    void like(Long userId, Long targetId, String targetType);

    /**
     * 取消点赞
     * @param userId
     * @param targetId
     * @param targetType
     */
    void unlike(Long userId, Long targetId, String targetType);

    boolean checkLike(Long userId, Long targetId, String targetType);
}
