package com.doublez.kc_forum.common.utiles;

public class RedisKeyUtil {

    private static final String SPLIT = ":";
    private static final String PREFIX_LIKES = "likes";
    private static final String FIELD_USER_TARGET_SET = "user_target_set"; // SADD userId to likes:user_target_set:article:123
    private static final String FIELD_TARGET_COUNT = "target_count";     // INCR likes:target_count:article:123

    /**
     * Key for the Set storing user IDs who liked a specific target.
     * e.g., likes:user_target_set:article:101 -> {userId1, userId2}
     */
    public static String getUserLikesTargetSetKey(String targetType, Long targetId) {
        return PREFIX_LIKES + SPLIT + FIELD_USER_TARGET_SET + SPLIT + targetType + SPLIT + targetId;
    }

    /**
     * Key for the String counter storing the like count for a specific target.
     * e.g., likes:target_count:article:101 -> 150
     */
    public static String getTargetLikeCountKey(String targetType, Long targetId) {
        return PREFIX_LIKES + SPLIT + FIELD_TARGET_COUNT + SPLIT + targetType + SPLIT + targetId;
    }
}