package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.request.UpdateArticleRequest;
import com.doublez.kc_forum.common.pojo.response.ArticleDetailResponse;
import com.doublez.kc_forum.common.pojo.response.ArticleMetaCacheDTO;
import com.doublez.kc_forum.common.pojo.response.UserArticleResponse;
import com.doublez.kc_forum.common.pojo.response.ViewArticleResponse;
import com.doublez.kc_forum.common.utiles.AssertUtil;
import com.doublez.kc_forum.common.utiles.RedisKeyUtil;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.IArticleService;

import com.doublez.kc_forum.service.IBoardService;
import com.doublez.kc_forum.service.IUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.doublez.kc_forum.service.impl.LikesServiceImpl.DB_PERSISTENCE_EXECUTOR;

@Slf4j
@Service
public class ArticleServiceImpl implements IArticleService {

    private static final long EMPTY_CACHE_TTL_MINUTES = 30;
    private static final String NULL_CACHE_MARKER = " ";
    @Autowired
    private ArticleMapper articleMapper;
    @Lazy
    @Autowired
    private IBoardService boardServiceImpl;//注意不可用造成循环引用
    @Lazy
    @Autowired
    private IUserService userServiceImpl;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RedisAsyncPopulationService redisAsync;

    // 定义Hash字段名称常量
    public static final String FIELD_ID = "id";
    public static final String FIELD_BOARD_ID = "boardId";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_IS_TOP = "isTop";
    public static final String FIELD_CREATE_TIME = "createTime";
    public static final String FIELD_UPDATE_TIME = "updateTime";
    public static final String FIELD_REPLY_COUNT = "replyCount";
    public static final String FIELD_LIKE_COUNT = "likeCount";
    public static final String FIELD_VISIT_COUNT = "visitCount";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Long getUserId(Long articleId) {
        //查询redis
        String articleKey = RedisKeyUtil.getArticleKey(articleId);
        Object userIdObj = redisTemplate.opsForHash().get(articleKey, FIELD_USER_ID);
        if (userIdObj != null) {
            return Long.parseLong(userIdObj.toString());
        }
        // Redis未命中，查询数据库
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>().select(Article::getUserId)
                .eq(Article::getId, articleId));
        if (article == null) {
            log.warn("文章不存在，无法查询对应用户id");
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }
        //todo // （可选）如果在数据库中找到但在Redis中未找到，则缓存文章（主要缓存在getArticleById中完成）
        return article.getUserId();
    }

    @Transactional
    @Override
    public void createArticle(Article article) {
        //非空校验
        if(article == null || article.getUserId() == null
                || article.getBoardId() == null
                || !StringUtils.hasText(article.getTitle())
                || !StringUtils.hasText(article.getContent())){
            log.error("转化后的文章缺少字段");
            throw new SystemException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        //赋予默认值,用于redis缓存
        article.setLikeCount(0);
        article.setVisitCount(0);
        article.setReplyCount(0);
        article.setState((byte)0);
        article.setDeleteState((byte)0);
        article.setCreateTime(LocalDateTime.now());

        //插入article
        int articleRow  = articleMapper.insert(article);
        if (articleRow != 1){
            log.warn(ResultCode.FAILED_CREATE.toString());
            throw new SystemException(ResultCode.FAILED_CREATE);
        }

        //更新用户发帖数量
        userServiceImpl.updateOneArticleCountById(article.getUserId(),1);

        //更新板块发帖数量
        boardServiceImpl.updateOneArticleCountById(article.getBoardId(),1);

        // 3. 将文章数据缓存到Redis
        cacheArticle(article);

        // 4. 将文章ID添加到板块的有序集合 (ZSET) 中
        String boardArticlesZSetKey = RedisKeyUtil.getBoardArticlesZSetKey(article.getBoardId());
        // 使用创建时间作为分数，以便按时间顺序排列 (如果使用reverseRange，则最新在前)
        // 或者，如果你更喜欢基于ID的排序并且你的ZSET查询逻辑与之匹配，也可以使用 article.getId()
        //todo ZoneOffset是什么？
        stringRedisTemplate.opsForZSet().add(boardArticlesZSetKey, article.getId().toString(), (double) article.getCreateTime().toEpochSecond(ZoneOffset.UTC));

        //打印日志
        log.info("发帖成功, 帖子id: {}, 用户id：{} ,板块id:{} " ,article.getId() ,article.getUserId(),article.getBoardId());

    }

    private void cacheArticle(Article article) {
        if (article == null || article.getId() == null) return;

        String articleKey = RedisKeyUtil.getArticleKey(article.getId());
        Map<String, Object> articleMap = new HashMap<>();
        // 注意：在Hash中将ID等数字类型存储为字符串，以保持一致性或避免某些客户端的潜在问题
        articleMap.put(FIELD_ID, article.getId().toString());
        articleMap.put(FIELD_BOARD_ID, article.getBoardId().toString());
        articleMap.put(FIELD_USER_ID, article.getUserId().toString());
        articleMap.put(FIELD_TITLE, article.getTitle());
        articleMap.put(FIELD_IS_TOP, article.getIsTop() != null ? article.getIsTop().toString() : "0");
        // 使用ISO-8601格式的日期时间字符串，RedisTemplate中的Jackson应该能正确处理JavaTimeModule
        articleMap.put(FIELD_CREATE_TIME, article.getCreateTime()); // Jackson会序列化
        articleMap.put(FIELD_UPDATE_TIME, article.getUpdateTime()); // Jackson会序列化
        articleMap.put(FIELD_REPLY_COUNT, article.getReplyCount() != null ? article.getReplyCount() : 0);
        articleMap.put(FIELD_LIKE_COUNT, article.getLikeCount() != null ? article.getLikeCount() : 0);
        articleMap.put(FIELD_VISIT_COUNT, article.getVisitCount() != null ? article.getVisitCount() : 0);

        redisTemplate.opsForHash().putAll(articleKey, articleMap); // 存入Hash

        // 单独缓存文章内容
        if (StringUtils.hasText(article.getContent())) {
            String contentKey = RedisKeyUtil.getArticleContentKey(article.getId());
            stringRedisTemplate.opsForValue().set(contentKey, article.getContent(), 1, TimeUnit.HOURS);
        }
    }

    /**
     * 将从Redis Hash获取的Map转换为Article对象 (或DTO)。
     * @param hashEntries 从Redis获取的Hash条目Map
     * @return 转换后的Article对象，如果输入为空或转换失败则为null
     */
    private Article mapToArticle(Map<Object, Object> hashEntries) {
        if (hashEntries == null || hashEntries.isEmpty()) {
            return null;
        }
        Article article = new Article();
        // 使用辅助方法安全地获取和转换值
        article.setId(getNullableLong(hashEntries.get(FIELD_ID)));
        article.setBoardId(getNullableLong(hashEntries.get(FIELD_BOARD_ID)));
        article.setUserId(getNullableLong(hashEntries.get(FIELD_USER_ID)));
        article.setTitle(getNullableString(hashEntries.get(FIELD_TITLE)));
        article.setIsTop(getNullableByte(hashEntries.get(FIELD_IS_TOP)));

        // RedisTemplate中的Jackson应该能正确反序列化JavaTimeModule支持的类型
        Object createTimeObj = hashEntries.get(FIELD_CREATE_TIME);
        if (createTimeObj instanceof LocalDateTime) { // Jackson可能直接反序列化为LocalDateTime
            article.setCreateTime((LocalDateTime) createTimeObj);
        } else if (createTimeObj != null) { // 可能是字符串或其他格式
            try {
                // 尝试将其作为ISO字符串解析，或根据你的存储格式调整
                article.setCreateTime(objectMapper.convertValue(createTimeObj, LocalDateTime.class));
            } catch (Exception e) {
                log.warn("无法将缓存中的createTime解析为LocalDateTime: {}，类型: {}", createTimeObj, createTimeObj.getClass().getName(), e);
            }
        }

        Object updateTimeObj = hashEntries.get(FIELD_UPDATE_TIME);
        if (updateTimeObj instanceof LocalDateTime) {
            article.setUpdateTime((LocalDateTime) updateTimeObj);
        } else if (updateTimeObj != null) {
            try {
                article.setUpdateTime(objectMapper.convertValue(updateTimeObj, LocalDateTime.class));
            } catch (Exception e) {
                log.warn("无法将缓存中的updateTime解析为LocalDateTime: {}，类型: {}", updateTimeObj, updateTimeObj.getClass().getName(), e);
            }
        }

        article.setReplyCount(getNullableInteger(hashEntries.get(FIELD_REPLY_COUNT)));
        article.setLikeCount(getNullableInteger(hashEntries.get(FIELD_LIKE_COUNT)));
        article.setVisitCount(getNullableInteger(hashEntries.get(FIELD_VISIT_COUNT)));
        return article;
    }

    // 安全转换的辅助方法
    private String getNullableString(Object obj) { return obj == null ? null : obj.toString(); }
    private Long getNullableLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long) return (Long) obj;
        try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { log.warn("无法将 '{}' 解析为 Long", obj); return null; }
    }
    private Integer getNullableInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        try { return Integer.parseInt(obj.toString()); } catch (NumberFormatException e) { log.warn("无法将 '{}' 解析为 Integer", obj); return null; }
    }
    private Byte getNullableByte(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Byte) return (Byte) obj;
        try { return Byte.parseByte(obj.toString()); } catch (NumberFormatException e) { log.warn("无法将 '{}' 解析为 Byte", obj); return null; }
    }

    public ViewArticleResponse getArticleCards(Long boardId, int currentPage, int pageSize){

        String boardArticlesZSetKey = RedisKeyUtil.getBoardArticlesZSetKey(boardId);
        long start = (long)(currentPage - 1) * pageSize; // ZSETs are 0-indexed
        long end = start + pageSize - 1;
        //1. 查询 articles id
        Long count = stringRedisTemplate.opsForZSet().zCard(boardArticlesZSetKey);
        List<Long> articleIdsInOrder;// 存储文章id
        if(count == null || count == 0){
            // 为空，尝试获取数据库
            List<Article> articlesFromDb = articleMapper.selectList(
                    new LambdaQueryWrapper<Article>()
                            .select(Article::getId,Article::getCreateTime)
                            .eq(Article::getBoardId, boardId)
                            .eq(Article::getDeleteState,0));
            if(articlesFromDb.isEmpty()){
                // 真的为空 直接返回，也可能board不存在
                //todo 缓存穿透
                return new ViewArticleResponse(null,0L);
            }
            // 异步回填到redis中
            redisAsync.boardZsetFromDBToRedis(boardArticlesZSetKey,articlesFromDb);

            articleIdsInOrder = articlesFromDb.stream().map(Article::getId).collect(Collectors.toList());
        }else {
            //不为空 从redis中查询
            Set<String> articleIdStrings = stringRedisTemplate.opsForZSet().reverseRange(boardArticlesZSetKey, start, end);
            if (CollectionUtils.isEmpty(articleIdStrings)) {
                // ZSET缓存未命中或该板块没有文章。可以尝试从数据库加载。
                // 这里的数据库查询理想情况下也应该分页（如果是为了“填充ZSET”）。
                // 为简单起见，如果ZSET为空，则假定此页面视图没有文章。
                // 一个更健壮的解决方案是使用定时任务或写穿透来填充ZSET。
                log.info("在ZSET {} 中未找到板块 {} 第 {} 页的文章ID", boardArticlesZSetKey, boardId, currentPage);

                return new ViewArticleResponse(null, 0L);
            }
            //这个 articleIds 列表保持了从ZSET获取的顺序，这是最终结果的顺序依据
            articleIdsInOrder = articleIdStrings.stream().map(Long::parseLong).toList();
        }
        // 2. 准备一个Map来存储按ID索引的文章，以及一个列表来收集未命中的ID
        Map<Long, Article> foundArticlesMap = new HashMap<>();
        List<Long> missedArticleIds = new ArrayList<>();

        // 3. 使用Pipeline批量从Redis获取文章Hash
        List<Object> articlesFromCachePipelined = redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (Long articleId : articleIdsInOrder) { // 按照有序ID列表的顺序请求
                        String articleKey = RedisKeyUtil.getArticleKey(articleId);
                        connection.hashCommands().hGetAll(articleKey.getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
                });
        // 4. 处理Pipeline的结果，区分命中和未命中
        for (int i = 0; i < articleIdsInOrder.size(); i++) {
            Long currentId = articleIdsInOrder.get(i);
            Object rawHashObject = (articlesFromCachePipelined != null && i < articlesFromCachePipelined.size()) ? articlesFromCachePipelined.get(i) : null;

            if (rawHashObject instanceof Map && !((Map<?, ?>) rawHashObject).isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> rawHash = (Map<Object, Object>) rawHashObject;
                Article article = mapToArticle(rawHash);
                if (article != null && article.getId() != null) {
                    foundArticlesMap.put(currentId, article); // 存入map，键是ID
                } else {
                    log.warn("文章数据从Redis Hash映射失败 (或映射结果ID为null), articleId: {}", currentId);
                    missedArticleIds.add(currentId);
                }
            } else {
                // 记录日志，rawHashObject可能为null或非Map类型
                if (rawHashObject == null) log.debug("Redis Pipeline结果中 articleId: {} 的数据为null (缓存未命中)", currentId);
                else log.debug("Redis Pipeline结果中 articleId: {} 的数据类型不为Map或为空Map (视为缓存未命中): {}", currentId, rawHashObject.getClass().getName());
                missedArticleIds.add(currentId);
            }
        }
        // 5. 从数据库批量获取未命中的文章并回填缓存
        if (!missedArticleIds.isEmpty()) {
            log.info("文章缓存未命中，将从数据库查询，ID列表: {}", missedArticleIds);
            // 确保数据库查询也考虑了 deleteState
            List<Article> dbArticles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                    .in(Article::getId, missedArticleIds)
                    .eq(Article::getDeleteState, 0)); // 确保文章未被删除

            for (Article dbArticle : dbArticles) {
                if (dbArticle != null && dbArticle.getId() != null) {
                    foundArticlesMap.put(dbArticle.getId(), dbArticle); // 将从DB获取的数据也放入map
                    log.info("从数据库获取文章 {} 成功，已回填缓存", dbArticle.getId());
                }
            }
            // 异步回填缓存
            redisAsync.cacheArticleList(dbArticles);
        }
        // 6. 按原始顺序组装最终的文章列表
        List<Article> finalOrderedArticles = new ArrayList<>();
        for (Long articleId : articleIdsInOrder) { // 遍历最初从ZSET获取的有序ID列表
            Article article = foundArticlesMap.get(articleId);
            if (article != null) {
                finalOrderedArticles.add(article);
            } else {
                // 如果在foundArticlesMap中仍然找不到 (意味着缓存和DB都没有，或者DB中是删除状态，或者在填充ZSET后被删了)
                // 这种情况需要记录，该文章将不会出现在当前页的列表中
                log.warn("文章ID: {} 在缓存和数据库中均未找到有效数据，将从结果中忽略", articleId);
            }
        }

        if (finalOrderedArticles.isEmpty()) {
            return new ViewArticleResponse(null,0L);
        }
        // 7. 获取作者信息
        List<Long> authorIds = finalOrderedArticles.stream()
                .map(Article::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserArticleResponse> userMap = fetchAndCacheUsers(authorIds);
        // 8. 组装响应
        List<ArticleMetaCacheDTO> responseList = new ArrayList<>();
        for (Article meta : finalOrderedArticles) { // 使用最终排序好的列表
            // ... (组装 ArticleMetaCacheDTO 的逻辑保持不变)
            // 确保从 meta 对象中获取所有需要的字段
            ArticleMetaCacheDTO resp = new ArticleMetaCacheDTO();
            resp.setId(meta.getId());
            resp.setBoardId(meta.getBoardId());
            resp.setUserId(meta.getUserId());
            resp.setTitle(meta.getTitle());
            resp.setIsTop(meta.getIsTop());
            resp.setCreateTime(meta.getCreateTime());
            resp.setUpdateTime(meta.getUpdateTime());
            resp.setVisitCount(meta.getVisitCount()); // 这些应该从Article对象中获取
            resp.setLikeCount(meta.getLikeCount());
            resp.setReplyCount(meta.getReplyCount());

            UserArticleResponse userCache = userMap.get(meta.getUserId());
            if (userCache == null) {
                log.warn("未找到用户ID: {} (文章ID: {}) 的用户信息，将跳过此文章的展示", meta.getUserId(), meta.getId());
                continue; // 跳过这个文章，不添加到最终结果
            }
            resp.setUser(userCache);
            responseList.add(resp);
        }
        return new ViewArticleResponse(responseList,count);


//        List<Long> articleIds;
//        //1.1 判空
//        if(CollectionUtils.isEmpty(articleIdStrings)){
//            //1.1.1查询数据库
//            List<Article> articles = articleMapper.selectList(
//                    new LambdaQueryWrapper<Article>()
//                            .select(Article::getId)
//                            .eq(Article::getBoardId, boardId).
//                            eq(Article::getDeleteState, 0));
//            articleIds = articles.stream().map(Article::getId).collect(Collectors.toList());
//            //1.1.2回填到redis中
//            stringRedisTemplate.opsForZSet().add(boardArticlesZSetKey,
//                            articleIds.stream().map(id -> new DefaultTypedTuple<>(id.toString(),id.doubleValue()))
//                            .collect(Collectors.toSet()));
//        }else {
//            //1.1.3 不为空 转化一下类型
//            articleIds = articleIdStrings.stream().map(Long::parseLong).toList();
//        }
//        //1.1.4 如果此时为空那就是真的为空
//        if(CollectionUtils.isEmpty(articleIds)){
//            return new ArrayList<>();
//        }
//        //2. 从redis中批量获取文章数据
//        //2.1 批量从Redis获取文章元数据缓存
//        List<ArticleMetaCacheDTO> articleMetaList = fetchAndCacheArticleMetasByIds(articleIds);
//
//        //2.2 去重作者id
//        List<Long> authorIdsList = articleMetaList.stream().
//                map(ArticleMetaCacheDTO::getUserId)
//                .filter(Objects::nonNull)
//                .collect(Collectors.toList());
//        //3. 获取作者信息缓存
//        Map<Long,UserArticleResponse> userMap = fetchAndCacheUsers(authorIdsList);
//
//        //4. 获取点赞数、访问量计数
//        Map<Long, Integer> visitCountsMap = fetchVisitUsingPipeline(articleIds);
//        Map<Long, Integer> likeCountsMap = fetchLikeUsingPipeline(articleIds);
//
//        //5. 组合 ArticleMetaCacheDTO 列表
//        List<ArticleMetaCacheDTO> responseList = new ArrayList<>();
//
//        for(int i = 0; i < articleMetaList.size(); i++){
//            ArticleMetaCacheDTO meta = articleMetaList.get(i);
//            if(meta == null){
//                log.warn("文章元数据为空，i:{}",i);
//                continue;
//            }
//            Long articleId = meta.getId();
//            ArticleMetaCacheDTO resp = new ArticleMetaCacheDTO();
//            resp.setId(meta.getId());
//            resp.setBoardId(meta.getBoardId());
//            resp.setUserId(meta.getUserId());
//            resp.setTitle(meta.getTitle());
//            resp.setIsTop(meta.getIsTop());
//            resp.setCreateTime(meta.getCreateTime());
//            resp.setUpdateTime(meta.getUpdateTime());
//            // 访问量和点赞数
//            resp.setVisitCount(visitCountsMap.getOrDefault(articleId, 0)); // 默认值处理
//            resp.setLikeCount(likeCountsMap.getOrDefault(articleId, 0)); // 默认值处理
//
//            //用户
//            UserArticleResponse userCache = userMap.get(meta.getUserId());
//            if(userCache == null){
//                log.warn("文章的用户信息不存在，articleId:{},userId:{}",articleId,meta.getUserId());
//                //todo 是否需要默认？
//                continue;
//            }
//            resp.setUser(userCache);
//            responseList.add(resp);
//        }
//        return responseList;
    }

//    private Map<Long, Integer> fetchLikeUsingPipeline(List<Long> articleIds) {
//        //1. 判空
//        if(CollectionUtils.isEmpty(articleIds)){
//            return Collections.emptyMap();
//        }
//        //2. key,提取初始化
//        List<String> likeKey = new ArrayList<>(articleIds.size());
//        for(Long articleId: articleIds){
//            likeKey.add(RedisKeyUtil.getTargetLikeCountKey("article",articleId));
//        }
//        //3. redis中查询
//        List<String> likeJsonList = stringRedisTemplate.opsForValue().multiGet(likeKey);
//
//        //3.1 没有查询到
//        if(CollectionUtils.isEmpty(likeJsonList)){
//            //todo
//            return Collections.emptyMap();
//        }
//        Map<Long, Integer> likeCountsMap = new HashMap<>(likeJsonList.size());
//        for(int i = 0; i < likeJsonList.size(); i++){
//            String likeJson = likeJsonList.get(i);
//            Long articleId = articleIds.get(i);
//            //todo visit 应该是long
//            likeCountsMap.put(articleId,Integer.parseInt(likeJson));
//        }
//        return likeCountsMap;
//    }

//    private Map<Long, Integer> fetchVisitUsingPipeline(List<Long> articleIds) {
//        //1. 判空
//        if(CollectionUtils.isEmpty(articleIds)){
//            return Collections.emptyMap();
//        }
//        //2. key,提取初始化
//        List<String> visitKey = new ArrayList<>(articleIds.size());
//        for(Long articleId: articleIds){
//            visitKey.add(RedisKeyUtil.getArticleVist(articleId));
//        }
//        //3. redis中查询
//        List<String> visitJsonList = stringRedisTemplate.opsForValue().multiGet(visitKey);
//
//        //3.1 没有查询到
//        if(CollectionUtils.isEmpty(visitJsonList)){
//            //todo
//            return Collections.emptyMap();
//        }
//        Map<Long, Integer> visitCountsMap = new HashMap<>(visitJsonList.size());
//        for(int i = 0; i < visitJsonList.size(); i++){
//            String visitJson = visitJsonList.get(i);
//            Long articleId = articleIds.get(i);
//            //todo visit 应该是long
//            visitCountsMap.put(articleId,Integer.parseInt(visitJson));
//        }
//        return visitCountsMap;
//    }

    /**
     * 获取并缓存用户信息。
     */
    private Map<Long, UserArticleResponse> fetchAndCacheUsers(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        Map<Long, UserArticleResponse> userMap = new HashMap<>();
        List<String> userKeys = userIds.stream().map(RedisKeyUtil::getUserResponseKey).toList();

        // 使用Pipeline的MGET获取用户JSON字符串
        List<Object> userJsonListObjects = stringRedisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (String userKey : userKeys) {
                        connection.stringCommands().get(userKey.getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
                });


        List<Long> missedUserIds = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) {
            Long currentUserId = userIds.get(i);
            // Pipeline返回的是List<Object>，需要处理null和类型转换
            String userJson = null;
            if (userJsonListObjects != null && i < userJsonListObjects.size() && userJsonListObjects.get(i) != null) {
                Object element = userJsonListObjects.get(i);
                if (element instanceof String) { // 主要检查 String 类型
                    userJson = (String) element;
                } else {
                    log.error("不是期望的用户数据类型:{} ", element.getClass().getName());
                }
            }
            if (StringUtils.hasText(userJson) && !"null".equalsIgnoreCase(userJson)) { // 检查 "null" 字符串
                try {
                    UserArticleResponse user = objectMapper.readValue(userJson, UserArticleResponse.class);
                    userMap.put(currentUserId, user);
                } catch (JsonProcessingException e) {
                    log.error("解析用户ID {} 的JSON失败: {}", currentUserId, e.getMessage());
                    missedUserIds.add(currentUserId);
                }
            } else {
                missedUserIds.add(currentUserId);
            }
        }

        if (!missedUserIds.isEmpty()) {
            log.info("用户缓存未命中，ID列表: {}", missedUserIds);

             Map<Long,User> dbUsers = userServiceImpl.selectUserInfoByIds(missedUserIds);
             for (User dbUser : dbUsers.values()) {
               UserArticleResponse uar = copyProperties(dbUser, UserArticleResponse.class);
               userMap.put(dbUser.getId(), uar);
               redisAsync.cacheUser(uar);
             }
        }
        return userMap;
    }
//    private Map<Long,UserArticleResponse> fetchAndCacheUsers(List<Long> authorIdsList) {
//        //1. 判空
//        if(CollectionUtils.isEmpty(authorIdsList)){
//            return Collections.emptyMap();
//        }
//        //2. key,提取初始化
//        List<String> userKey = new ArrayList<>(authorIdsList.size());
//        for(Long authorId: authorIdsList){
//            userKey.add(RedisKeyUtil.getUserResponseKey(authorId));
//        }
//        //3. redis中查询
//        List<String> userJsonList = stringRedisTemplate.opsForValue().multiGet(userKey);
//        //todo 名字是否要改
//        Map<Long, UserArticleResponse> userMap = new HashMap<>(userJsonList.size());
//
//        //3.1 没有查询到
//        if(CollectionUtils.isEmpty(userJsonList)){
//            //todo
//            return Collections.emptyMap();
//        }
//
//        for(String userJson: userJsonList){
//            try {
//                UserArticleResponse userCacheDTO = objectMapper.readValue(userJson,UserArticleResponse.class);
//                if(userCacheDTO != null){
//                    userMap.put(userCacheDTO.getId(), userCacheDTO);
//                }else{
//                    log.warn("用户数据反序列化后为 null (JSON string was 'null'), userJson: {}", userJson);
//                }
//            } catch (JsonProcessingException e) {
//                log.error("用户数据(UserCacheDTO)转化异常, json: [{}], e:{}", userJson, e.getMessage());
//            }
//        }
//        return userMap;
//    }

//    private List<ArticleMetaCacheDTO> fetchAndCacheArticleMetasByIds(List<Long> articleIds) {
//        //1. 判空
//        if(CollectionUtils.isEmpty(articleIds)){
//            return Collections.emptyList();
//        }
//        //2. key,提取初始化
//        List<String> metaKeyList = articleIds.stream().map(RedisKeyUtil::getArticleMeta).collect(Collectors.toList());
//        //3. redis中进行查询
//        List<String> metaCache = stringRedisTemplate.opsForValue().multiGet(metaKeyList);
//
//        Map<Long, ArticleMetaCacheDTO> resultMap = new HashMap<>();
//        List<Long> missedIds = new ArrayList<>();
//        Map<String, ArticleMetaCacheDTO> metasToCache = new HashMap<>();
//
//        for (int i = 0; i < metaCache.size(); i++) {
//            String currentMetaJson = metaCache.get(i);
//            if (currentMetaJson != null) {
//                resultMap.put();
//            } else {
//                missedIds.add(currentId);
//            }
//        }
//
//        //3.1 没有查询到
//        if(CollectionUtils.isEmpty(metaCache)){
//            log.warn("未在redis中查询到文章元数据");
//            //查询数据库
//            return ;
//        }
//        //4. 返回
//        return metaCache.stream().map(o -> {
//            if(o == null){
//                //查询数据库回填数据
//                return null;
//            }
//            try {
//                return objectMapper.readValue(o,ArticleMetaCacheDTO.class);
//            } catch (JsonProcessingException e) {
//                log.error("文章meta元数据转化移除，e:{}",e.getMessage());
//                return null;
//            }
//        }).toList();
//
//    }

//    public Page<ArticleMetaCacheDTO> getArticleCardsByBoard(Long boardId, int currentPage, int pageSize) {
//        String boardArticlesZSetKey = RedisKeyUtil.getBoardArticlesZSetKey(boardId);
//        long start = (long)(currentPage - 1) * pageSize; // ZSETs are 0-indexed
//        long end = start + pageSize - 1;
//
//        // 1. 从 Redis ZSET 获取文章 ID 列表 (按score降序，假设score是时间戳)
//        Set<String> articleIdStrings = stringRedisTemplate.opsForZSet().reverseRange(boardArticlesZSetKey, start, end);
//
//        List<Long> articleIds;
//        long totalArticlesInBoard; // 用于分页总数
//
//        if (CollectionUtils.isEmpty(articleIdStrings)) {
//            // todo ZSET 为空或不存在，可以考虑从数据库加载并回填ZSET (冷启动或缓存重建)
//            // 并可选地填充 ZSET
//            Page<Article> articlesFromDbFallback = fallbackToDbAndPotentiallyWarmZSet(boardId, currentPage, pageSize, boardArticlesZSetKey);
//            if (articlesFromDbFallback == null || CollectionUtils.isEmpty(articlesFromDbFallback.getRecords())) {
//                return new Page<>(currentPage, pageSize, 0);
//            }
//            articleIds = articlesFromDbFallback.getRecords().stream().map(Article::getId).collect(Collectors.toList());
//            totalArticlesInBoard = articlesFromDbFallback.getTotal();
//        } else {
//            articleIds = articleIdStrings.stream().map(Long::parseLong).collect(Collectors.toList());
//            // 获取 ZSET 中的总文章数用于分页
//            Long zsetSize = stringRedisTemplate.opsForZSet().zCard(boardArticlesZSetKey);
//            totalArticlesInBoard = (zsetSize != null) ? zsetSize : 0;
//        }
//
//        if (CollectionUtils.isEmpty(articleIds)) {
//            return new Page<>(currentPage, pageSize, totalArticlesInBoard);
//        }
//
//        // --- 后续步骤与之前类似，但获取 Article 基础信息的方式可能变化 ---
//
//        // 2. 批量从Redis获取文章元数据缓存
//        // 注意：现在我们只有ID，所以如果ArticleMetaCacheDTO未命中，需要从DB根据ID批量获取Article
//        Map<Long, ArticleMetaCacheDTO> articleMetaMap = fetchAndCacheArticleMetasByIds(articleIds);
//
//        // 收集作者ID
//        Set<Long> authorIdsSet = articleMetaMap.values().stream()
//                .map(ArticleMetaCacheDTO::getUserId)
//                .filter(Objects::nonNull)
//                .collect(Collectors.toSet());
//
//        // 3. 批量获取用户信息缓存
//        Map<Long, UserCacheDTO> userMap = fetchAndCacheUsers(authorIdsSet); // 这个方法可以复用
//
//        // 4. 批量获取各项计数
//        Map<Long, Integer> visitCountsMap = fetchCountsUsingPipeline(articleIds, RedisKeyUtil::getArticleRead);
//        Map<Long, Integer> likeCountsMap = fetchCountsUsingPipeline(articleIds, id -> RedisKeyUtil.getTargetLikeCountKey(RedisKeyUtil.TARGET_TYPE_ARTICLE, id));
//        // replyCount 仍然需要从 ArticleMetaCacheDTO 或 Article 实体获取 (如果它在这些对象中)
//        // 或者你也为 replyCount 维护单独的 Redis 计数器
//
//        // 5. 组装 ArticleMetaCacheDTO 列表
//        List<ArticleMetaCacheDTO> responseList = new ArrayList<>();
//        for (Long articleId : articleIds) { // 保持从 ZSET 获取的顺序
//            ArticleMetaCacheDTO meta = articleMetaMap.get(articleId);
//            if (meta == null) {
//                // 理论上 fetchAndCacheArticleMetasByIds 应该处理了这个问题
//                // 如果仍然为 null，可能意味着文章在获取meta时被删除了，或者DB中不存在
//                System.err.println("Warning: ArticleMetaCacheDTO not found for articleId: " + articleId + " after attempting fetch.");
//                continue;
//            }
//
//            ArticleMetaCacheDTO resp = new ArticleMetaCacheDTO();
//            resp.setId(meta.getId());
//            resp.setBoardId(meta.getBoardId()); // meta DTO 需要包含 boardId
//            resp.setUserId(meta.getUserId());
//            resp.setTitle(meta.getTitle());
//            resp.setIsTop(meta.getIsTop());
//            resp.setCreateTime(meta.getCreateTime());
//            resp.setUpdateTime(meta.getUpdateTime());
//
//            // 访问量和点赞数
//            resp.setVisitCount(visitCountsMap.getOrDefault(articleId, 0)); // 默认值处理
//            resp.setLikeCount(likeCountsMap.getOrDefault(articleId, 0)); // 默认值处理
//
//            // 回复数: 假设 ArticleMetaCacheDTO 包含 replyCount
//            // 如果不包含，你需要决定从哪里获取。如果DB是权威，且ArticleMetaCacheDTO从DB同步，它应该有。
//            // 或者像其他计数一样，单独在Redis维护。
//            // resp.setReplyCount(meta.getReplyCount()); // 假设 meta 包含
//
//            // 从DB获取的Article实体中取replyCount (如果meta中没有)
//            // 这个场景下，如果meta miss后从DB加载了Article，replyCount就有了
//            // 如果你的 ArticleMetaCacheDTO 设计为包含 replyCount，这里就可以直接用
//            // 为简化，我们假设 ArticleMetaCacheDTO 中包含了 replyCount
//            if (meta instanceof ArticleMetaWithReplyCount) { // 假设有这样一个子类或字段
//                resp.setReplyCount(((ArticleMetaWithReplyCount)meta).getReplyCount());
//            } else {
//                // Fallback: 如果你的ArticleMetaCacheDTO不直接存replyCount，
//                // 你可能需要从数据库加载的完整Article对象中获取，或者默认为0，或者单独查询
//                // 在 fetchAndCacheArticleMetasByIds 中如果从DB回填，应确保replyCount被填充
//                // 暂时设定为0，需要根据你的 DTO 设计调整
//                resp.setReplyCount(0);
//            }
//
//
//            UserCacheDTO userCache = userMap.get(meta.getUserId());
//            if (userCache != null) {
//                UserArticleResponse userResp = convertUserCacheToResponse(userCache);
//                resp.setUser(userResp);
//            } else if (meta.getUserId() != null) {
//                UserArticleResponse defaultUser = new UserArticleResponse();
//                defaultUser.setId(meta.getUserId());
//                defaultUser.setNickName("用户不存在"); // 或者其他默认提示
//                resp.setUser(defaultUser);
//            }
//            responseList.add(resp);
//        }
//
//        Page<ArticleMetaCacheDTO> resultPage = new Page<>(currentPage, pageSize, totalArticlesInBoard);
//        resultPage.setRecords(responseList);
//        return resultPage;
//    }

//    public List<ArticleMetaCacheDTO> getAllArticlesByBoardId(@RequestParam(required = false) Long id) {
//        //1. 获取id
//        List<Article> articleIds = articleMapper.selectList(
//                new LambdaQueryWrapper<Article>()
//                        .select(Article::getId)
//                        .eq(Article::getBoardId, id)
//                        .eq(Article::getDeleteState, 0));
//        //2. 存储key
//        List<String> metaKeysList = new ArrayList<>();
//        List<String> ReadKeysList = new ArrayList<>();
//        List<String> LikeKeysList = new ArrayList<>();
//        for(Article article : articleIds){
//            metaKeysList.add(RedisKeyUtil.getArticleMeta(article.getId()));
//            ReadKeysList.add(RedisKeyUtil.getArticleRead(article.getId()));
//            LikeKeysList.add(RedisKeyUtil.getTargetLikeCountKey("article", article.getId()));
//        }
//        //3. pipeline 查询redis
//        stringRedisTemplate.executePipelined(
//                (RedisCallback<Object>) connection ->{
//                    for(String metaKey : metaKeysList){
//                        connection.hGetAll()
//                    }
//                }
//        )
//    }
    @Override
    public List<ArticleMetaCacheDTO> getAllArticlesByBoardId(@RequestParam(required = false) Long id) {
        // 1. 查询 Article 表,
        // 可以使用 Optional 来处理 id 为空的情况，使代码更简洁。但是这样好像会导致多查数据？？
        List<Article> articles;
        if (id == null) {
            // 查询所有文章
            articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                    .select(Article::getId, Article::getBoardId, Article::getUserId, Article::getTitle,
                            Article::getVisitCount, Article::getReplyCount, Article::getLikeCount,
                            Article::getIsTop, Article::getCreateTime, Article::getUpdateTime)
                    .eq(Article::getDeleteState, 0)
                    .eq(Article::getState, 0));
        } else {
            // 先判断板块是否存在
            Board board = boardServiceImpl.selectOneBoardById(id);
            if (board == null) {
                log.warn("板块不存在 boardId: {}",id);
                throw new SystemException(ResultCode.FAILED_BOARD_NOT_EXISTS);
            }
            // 根据 boardId 查询文章
            articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                    .select(Article::getId, Article::getBoardId, Article::getUserId, Article::getTitle,
                            Article::getVisitCount, Article::getReplyCount, Article::getLikeCount,
                            Article::getIsTop, Article::getCreateTime, Article::getUpdateTime)
                    .eq(Article::getBoardId, id)
                    .eq(Article::getDeleteState, 0)
                    .eq(Article::getState, 0));
        }

        //判断为空直接返回
        if(articles == null || articles.isEmpty()){
            log.warn("板块帖子为空");
            return Collections.emptyList();
        }
        // 2. 提取所有 userId
        List<Long> userIds = articles.stream()
                .map(Article::getUserId)
                .distinct() // 去重
                .collect(Collectors.toList());

        Map<Long,User> userMap = userServiceImpl.selectUserInfoByIds(userIds);

        // 3. 组装数据

        List<ArticleMetaCacheDTO> articleMetaCacheDTO = articles.stream().map(article -> {
            User user = userMap.get(article.getUserId());
            //判断用户是否存在
            AssertUtil.checkClassNotNull(user,ResultCode.FAILED_USER_NOT_EXISTS,article.getId());

            UserArticleResponse userArticleResponse = copyProperties(user, UserArticleResponse.class);
            com.doublez.kc_forum.common.pojo.response.ArticleMetaCacheDTO viewArticleResponse = copyProperties(article, com.doublez.kc_forum.common.pojo.response.ArticleMetaCacheDTO.class);

            viewArticleResponse.setUser(userArticleResponse);

            return viewArticleResponse;
        }).collect(Collectors.toList());

        log.info("{}:查询帖子成功", ResultCode.SUCCESS.getMessage());
        return articleMetaCacheDTO;
    }

    @Override
    public ArticleDetailResponse getArticleDetailById(Long userId, Long articleId) {
        //1. 查询redis meta 和 content
        String articleContentKey = RedisKeyUtil.getArticleContentKey(articleId);
        String content = stringRedisTemplate.opsForValue().get(articleContentKey);

        //1.1 判断是否是缓存穿透情况
        if(NULL_CACHE_MARKER.equals(content)){
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }

        String articleMetaKey = RedisKeyUtil.getArticleKey(articleId);
        Map<Object, Object> articleMetaMap = redisTemplate.opsForHash().entries(articleMetaKey);

        //最后返回的结果
        ArticleDetailResponse articleDetailResponse = null;
        //2. 有一个为空就直接查数据库
        if(content == null|| CollectionUtils.isEmpty(articleMetaMap)){
            log.debug("文章{} 未缓存，查询数据库",articleId);
            //2.1 查询数据库
            Article article = articleMapper.selectOne(
                    new LambdaQueryWrapper<Article>()
                            .eq(Article::getId, articleId)
                            .eq(Article::getDeleteState, 0).eq(Article::getState, 0));
            //2.2 文章确实不存在，进行执行缓存穿透
            if(article == null){
                stringRedisTemplate.opsForValue().set(articleContentKey," ",EMPTY_CACHE_TTL_MINUTES , TimeUnit.MINUTES);
                throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
            }
            articleDetailResponse = copyProperties(article, ArticleDetailResponse.class);
            //2.2 异步存入缓存
            redisAsync.cacheArticleList(List.of(article));
        }else{
            //3. 不为空，进行转化类型，获取 articleDetailResponse
            Map<String,Object> stringKeyMap = new HashMap<>();

            for (Map.Entry<Object, Object> entry : articleMetaMap.entrySet()) {
                if (entry.getKey() instanceof String) {
                    // 如果值也是 String，并且目标是 LocalDateTime，Jackson 会尝试用标准格式解析
                    // 如果值已经是正确类型（例如，通过某种方式存入时就是 Long），则更好
                    stringKeyMap.put((String) entry.getKey(), entry.getValue());
                } else {
                    // 处理非 String 类型的键，如果确定都是 String，可以简化
                    stringKeyMap.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                try {
                    articleDetailResponse = objectMapper.convertValue(stringKeyMap,ArticleDetailResponse.class);
                    articleDetailResponse.setContent(content);
                } catch (Exception e) {
                    log.error("stringKeyMap 转化至 ArticleDetailResponse 失败：{}",stringKeyMap,e);
                    throw new SystemException(ResultCode.ERROR_REDIS_CHANGE);
                }
            }
        }
        //4. 获取用户
        String userJson = stringRedisTemplate.opsForValue().get(RedisKeyUtil.getUserResponseKey(articleDetailResponse.getUserId()));
        //4.1 判断是否命中缓存
        if(StringUtils.hasText(userJson)){
            try {
                articleDetailResponse.setUser(objectMapper.readValue(userJson,UserArticleResponse.class));
            } catch (JsonProcessingException e) {
                log.error("解析用户ID {} 的JSON失败: {}", articleDetailResponse.getUserId(), e.getMessage());
            }
        }else {
            //4.2 未命中查询数据库
            User user = userServiceImpl.selectUserInfoById(articleDetailResponse.getUserId());
            AssertUtil.checkClassNotNull(user,ResultCode.FAILED_USER_NOT_EXISTS,articleDetailResponse.getUserId());
            UserArticleResponse userArticleResponse = copyProperties(user, UserArticleResponse.class);
            articleDetailResponse.setUser(userArticleResponse);
            //4.3 异步缓存用户
            redisAsync.cacheUser(userArticleResponse);
        }

        //判断是否是本人帖子，用于设置权限
        if(userId.equals(articleDetailResponse.getUserId())){
            articleDetailResponse.setOwn(true);
        }
        //异步增加访问数量
        redisAsync.incrVisit(articleDetailResponse.getId());
        //更新返回给前端的帖子访问次数
        articleDetailResponse.setVisitCount(articleDetailResponse.getVisitCount()+1);

        log.info("查询帖子详情成功, articleId:{}",articleId);
        return articleDetailResponse;
    }

    @Override
    public List<ArticleMetaCacheDTO> getAllArticlesByUserId(Long userId) {
        //根据用户id查询
        List<Article> articles = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, userId)
                .eq(Article::getDeleteState, 0));
        //为空直接返回
        if(articles == null || articles.isEmpty()){
            return Collections.emptyList();
        }
        //组装数据
        User user = userServiceImpl.selectUserInfoById(userId);
        //判断为空
        if(user == null){
            log.warn("用户不存在, userId:{} ",userId);
            throw new BusinessException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        UserArticleResponse userArticleResponse =copyProperties(user, UserArticleResponse.class);
        List<com.doublez.kc_forum.common.pojo.response.ArticleMetaCacheDTO> viewArticlesRespons = articles.stream().map(article -> {
            com.doublez.kc_forum.common.pojo.response.ArticleMetaCacheDTO articleMetaCacheDTO = copyProperties(article, com.doublez.kc_forum.common.pojo.response.ArticleMetaCacheDTO.class);
            articleMetaCacheDTO.setUser(userArticleResponse);

            return articleMetaCacheDTO;
        }).toList();
        log.info("查询用户发布帖子成功：userId:{}",userId);
        return viewArticlesRespons;
    }

    @Transactional
    @Override
    public boolean updateArticle(UpdateArticleRequest updateArticleRequest) {
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getDeleteState, Article::getState)
                .eq(Article::getId,updateArticleRequest.getId()));
        if( article.getState() == 1  ){
            log.warn("帖子被禁言, id:{}",updateArticleRequest.getId());
            throw new BusinessException(ResultCode.FAILED_ARTICLE_BANNED);
        }else if(  article.getDeleteState() == 1 ){
            log.warn("帖子被删除, id:{}",updateArticleRequest.getId());
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }
        if(articleMapper.updateById(copyProperties(updateArticleRequest, Article.class)) == 1){
            log.info("帖子更新成功：{}",updateArticleRequest.getId());
            return true;
        }
        throw new SystemException(ResultCode.FAILED_UPDATE_ARTICLE);
    }

    @Transactional
    @Override
    public boolean deleteArticle(Long id) {
        //鉴权由controller层负责

        // 先检查记录是否存在
        Article article = articleMapper.selectById(id);
        if (article == null) {
            log.warn("删除帖子失败, 帖子不存在, id: {}", id);
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }
        if(article.getDeleteState() == 1){
            log.error("帖子已经被删除，id：{}",id);
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }
        // 执行删除操作
        if( articleMapper.update(new LambdaUpdateWrapper<Article>()
                .set(Article::getDeleteState, 1)
                .eq(Article::getId, id)) != 1){
            log.warn("删除文章失败, id: {}", id);
            throw new SystemException(ResultCode.FAILED_ARTICLE_DELETE);
        }

        //更新用户发帖数量
        userServiceImpl.updateOneArticleCountById(article.getUserId(),-1);

        //更新板块发帖数量
        boardServiceImpl.updateOneArticleCountById(article.getBoardId(),-1);

        //打印日志
        log.info("删帖成功,帖子id: {} ,用户id：{}, 板块id:{}",article.getId(), article.getUserId() ,article.getBoardId());
        return true;
    }
    @Override
    public int updateLikeCount(Long targetId, int increment){
        return articleMapper.update(new LambdaUpdateWrapper<Article>()
                .eq(Article::getId,targetId)
                .eq(Article::getDeleteState,0)
                .eq(Article::getState,0)
                .setSql("like_count = like_count + " + increment));
    }
    // 类型转化抽取出来的通用方法
    private <Source, Target> Target copyProperties(Source source, Class<Target> targetClass) {
        try {
            Target target = targetClass.getDeclaredConstructor().newInstance(); // 使用反射创建实例
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            log.error("类型转换失败: {} -> {}", source.getClass().getName(), targetClass.getName(), e);
            throw new SystemException(ResultCode.ERROR_TYPE_CHANGE);
        }
    }
}
