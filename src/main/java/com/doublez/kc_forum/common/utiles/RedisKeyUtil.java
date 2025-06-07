package com.doublez.kc_forum.common.utiles;

public class RedisKeyUtil {

    private static final String SPLIT = ":"; // 分隔符
    private static final String PREFIX_ARTICLE = "article"; // 文章前缀
    private static final String PREFIX_BOARD = "board";   // 板块前缀
    private static final String PREFIX_USER = "user";     // 用户前缀
    private static final String PREFIX_LIKES = "likes";   // 点赞相关前缀（用于点赞者集合）

    // 文章相关
    private static final String FIELD_CONTENT = "content";      // 内容字段
    private static final String FIELD_LIKERS_SET = "likers"; // 点赞某文章的用户ID集合

    // 板块相关
    private static final String FIELD_ARTICLES_ZSET = "articles:zset"; // 板块下的文章ID集合 (ZSET)，保持不变

    /**
     * 存储所有文章元数据和计数的 Hash 的 Key。
     * 例如: article:101 -> HASH {id:101, title:"..", likeCount:10, ...}
     */
    public static String getArticleKey(Long articleId) {
        return PREFIX_ARTICLE + SPLIT + articleId;
    }

    /**
     * 存储文章内容的 String 的 Key。
     * 例如: article:content:101 -> "文章正文..."
     */
    public static String getArticleContentKey(Long articleId) {
        return PREFIX_ARTICLE + SPLIT + FIELD_CONTENT + SPLIT + articleId;
    }

    /**
     * 存储点赞了特定文章的用户 ID 集合 (Set) 的 Key。
     * 例如: article:likers:101 -> {userId1, userId2}
     * 这取代了之前的 `likes:user_target_set:article:{id}`，使得与文章相关的上下文更清晰。
     * 如果你喜欢旧的命名方式，可以保留，但Lua脚本需要知道文章的 *主哈希键* 来更新其 likeCount 字段。
     */
    public static String getArticleLikersSetKey(Long articleId) {
        return PREFIX_ARTICLE + SPLIT + FIELD_LIKERS_SET + SPLIT + articleId;
    }

    // --- 通用目标点赞的 Key (如果你也需要点赞评论等) ---
    // 如果你有其他可点赞的实体，可以保留这些。
    // 对于文章，likeCount 将在文章的哈希中。
    // 点赞某文章的用户集合将是 article:likers:{articleId}

    /**
     * 存储点赞了特定通用目标的用户 ID 集合 (Set) 的 Key。
     * 例如: likes:user_target_set:comment:505 -> {userId1, userId2}
     */
    public static String getUserLikesTargetSetKey(String targetType, Long targetId) {
        return PREFIX_LIKES + SPLIT + "user_target_set" + SPLIT + targetType + SPLIT + targetId;
    }

    /**
     * 通用目标的点赞计数的 Key (如果该计数不属于目标主哈希的一部分)。
     * 对于文章，这个 Key *不会*被使用，因为 likeCount 在 article:{id} 哈希中。
     * 例如: likes:target_count:comment:505 -> 20
     */
    public static String getTargetLikeCountKey(String targetType, Long targetId) {
        // 如果你有其他可点赞的实体（例如评论），它们的计数可能不嵌入其主要的Redis哈希中，
        // 那么这个键结构可能仍然有用。
        // 对于文章，likeCount 现在是 article:{id} 哈希的一部分。
        return PREFIX_LIKES + SPLIT + "target_count" + SPLIT + targetType + SPLIT + targetId;
    }


    // --- 板块和用户的 Key (看起来没问题) ---
    public static String getBoardArticlesZSetKey(Long boardId) {
        return PREFIX_BOARD + SPLIT + FIELD_ARTICLES_ZSET + SPLIT + boardId;
    }

    public static String getUserResponseKey(Long userId) {
        return PREFIX_USER + SPLIT + userId;
    }
}