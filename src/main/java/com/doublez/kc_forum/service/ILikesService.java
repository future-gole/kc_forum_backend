package com.doublez.kc_forum.service;



public interface ILikesService {

    String TARGET_TYPE_ARTICLE = "article";
    String TARGET_TYPE_REPLY = "reply";

    /**
     * 点赞
     * @param userId 用户ID
     * @param targetId 目标ID (文章或回复ID)
     * @param targetType 目标类型 ("article", "reply")
     */
    void like(Long userId, Long targetId, String targetType);

    /**
     * 取消点赞
     * @param userId 用户ID
     * @param targetId 目标ID
     * @param targetType 目标类型
     */
    void unlike(Long userId, Long targetId, String targetType);

    /**
     * 检查用户是否已点赞
     * @param userId 用户ID
     * @param targetId 目标ID
     * @param targetType 目标类型
     * @return true 如果已点赞, false otherwise
     */
    boolean checkLikeStatus(Long userId, Long targetId, String targetType);

}
