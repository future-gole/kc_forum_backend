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

import static com.doublez.kc_forum.common.utiles.AssertUtil.copyProperties;

@Slf4j
@Service
public class ArticleServiceImpl implements IArticleService {


    private static final String NULL_CACHE_MARKER = " ";
    private static final String EMPTY_CACHE_CONTENT = " ";
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

    @Autowired
    private DBAsyncPopulationService dbAsync;

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

    // 定义一个常量作为空结果的占位符
    private static final String EMPTY_ARTICLE_ID_PLACEHOLDER = "-1";
    // 定义空结果缓存的过期时间（15分钟）
    private static final long EMPTY_CACHE_TTL_MINUTES = 15;
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

    public ViewArticleResponse getArticleCards(Long boardId, int currentPage, int pageSize) {

        String boardArticlesZSetKey = RedisKeyUtil.getBoardArticlesZSetKey(boardId);
        long start = (long) (currentPage - 1) * pageSize; // ZSETs are 0-indexed
        long end = start + pageSize - 1;
        //1. 查询 articles id
        Long count = stringRedisTemplate.opsForZSet().zCard(boardArticlesZSetKey);
        List<Long> articleIdsInOrder;// 存储文章id
        if (count == null || count == 0) {
            log.debug("board中没有缓存文章数据，尝试从数据库中获取，board:{}",boardId);
            // 为空，尝试获取数据库
            //todo 分页查询
            List<Article> articlesFromDb = articleMapper.selectList(
                    new LambdaQueryWrapper<Article>()
                            .select(Article::getId, Article::getCreateTime)
                            .eq(Article::getBoardId, boardId)
                            .eq(Article::getDeleteState, 0));
            if (articlesFromDb.isEmpty()) {
                // 真的为空 直接返回，也可能board不存在
                //进行缓存穿透
                stringRedisTemplate.opsForZSet().add(boardArticlesZSetKey, EMPTY_ARTICLE_ID_PLACEHOLDER, 0.0);
                stringRedisTemplate.expire(boardArticlesZSetKey, EMPTY_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                log.info("板块 {} 无文章，已设置缓存穿透占位符 {}，TTL {} 分钟", boardId, EMPTY_ARTICLE_ID_PLACEHOLDER, EMPTY_CACHE_TTL_MINUTES);
                return new ViewArticleResponse(null, 0L);
            }
            //设置count的值
            count = (long) articlesFromDb.size();
            // 异步回填到redis中
            redisAsync.boardZsetFromDBToRedis(boardArticlesZSetKey, articlesFromDb);
            //todo 这边不用再转了
            articleIdsInOrder = articlesFromDb.stream().map(Article::getId).collect(Collectors.toList());
        } else {
            //不为空 从redis中查询
            Set<String> articleIdStrings = stringRedisTemplate.opsForZSet().reverseRange(boardArticlesZSetKey, start, end);
            //判断是否是缓存空结果的占位符
            if (articleIdStrings != null && articleIdStrings.size() == 1 && articleIdStrings.contains(EMPTY_ARTICLE_ID_PLACEHOLDER)) {
                log.info("从ZSET {} 中获取到缓存穿透占位符，板块 {} 无文章", boardArticlesZSetKey, boardId);
                return new ViewArticleResponse(null, 0L); // 返回空结果
            }

            if (CollectionUtils.isEmpty(articleIdStrings)) {
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
                if (rawHashObject == null)
                    log.debug("Redis Pipeline结果中 articleId: {} 的数据为null (缓存未命中)", currentId);
                else
                    log.debug("Redis Pipeline结果中 articleId: {} 的数据类型不为Map或为空Map (视为缓存未命中): {}", currentId, rawHashObject.getClass().getName());
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
                    log.info("从数据库获取文章 {} 成功，开始回填缓存", dbArticle.getId());
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
            return new ViewArticleResponse(null, 0L);
        }
        // 7. 获取作者信息
        List<Long> authorIds = finalOrderedArticles.stream()
                .map(Article::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, UserArticleResponse> userMap = userServiceImpl.fetchAndCacheUsers(authorIds);
        // 8. 组装响应
        List<ArticleMetaCacheDTO> responseList = new ArrayList<>();
        for (Article meta : finalOrderedArticles) { // 使用最终排序好的列表
            // 确保从 meta 对象中获取所有需要的字段
            ArticleMetaCacheDTO resp = new ArticleMetaCacheDTO();
            resp.setId(meta.getId());
            resp.setBoardId(meta.getBoardId());
            resp.setUserId(meta.getUserId());
            resp.setTitle(meta.getTitle());
            resp.setIsTop(meta.getIsTop());
            resp.setCreateTime(meta.getCreateTime());
            resp.setUpdateTime(meta.getUpdateTime());
            resp.setVisitCount(meta.getVisitCount());
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
        return new ViewArticleResponse(responseList, count);
    }

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
            log.info("命中缓存穿透：articleId: {}",articleId);
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }

        String articleMetaKey = RedisKeyUtil.getArticleKey(articleId);
        Map<Object, Object> articleMetaMap = redisTemplate.opsForHash().entries(articleMetaKey);

        //最后返回的结果
        ArticleDetailResponse articleDetailResponse = null;
        //2. 有一个为空就直接查数据库
        if(content == null|| CollectionUtils.isEmpty(articleMetaMap)){
            log.info("文章 {} 未缓存，查询数据库",articleId);
            //2.1 查询数据库
            Article article = articleMapper.selectOne(
                    new LambdaQueryWrapper<Article>()
                            .eq(Article::getId, articleId)
                            .eq(Article::getDeleteState, 0).eq(Article::getState, 0));
            //2.2 文章确实不存在，进行执行缓存穿透
            if(article == null){
                log.debug("查询到不存在的文章，进行缓存穿透设置 content:{},articleId:{},TTL:{} 分钟",EMPTY_CACHE_CONTENT,articleId,EMPTY_CACHE_TTL_MINUTES);
                stringRedisTemplate.opsForValue().set(articleContentKey,EMPTY_CACHE_CONTENT,EMPTY_CACHE_TTL_MINUTES , TimeUnit.MINUTES);
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
                log.info("用户缓存命中，进行类型转化，id:{}",articleDetailResponse.getUserId());
                articleDetailResponse.setUser(objectMapper.readValue(userJson,UserArticleResponse.class));
            } catch (JsonProcessingException e) {
                log.error("解析用户ID {} 的JSON失败: {}", articleDetailResponse.getUserId(), e.getMessage());
            }
        }else {
            //4.2 未命中查询数据库
            log.info("用户缓存未命中，查询数据库，id:{}",articleDetailResponse.getUserId());
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
        //异步增加redis访问数量
        redisAsync.incrVisit(articleDetailResponse.getId());
        //todo 异步增加数据库访问数量
        dbAsync.updateArticleVisitCount(articleDetailResponse.getId());
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
        //1. 更新数据库
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
        LocalDateTime now = LocalDateTime.now();
        int update = articleMapper.update(new LambdaUpdateWrapper<Article>()
                .set(Article::getTitle, updateArticleRequest.getTitle())
                .set(Article::getContent, updateArticleRequest.getContent())

                .set(Article::getUpdateTime, now)
                .eq(Article::getId, updateArticleRequest.getId()));
        if(update != 1){
            log.error("帖子更新失败,articleId:{}",updateArticleRequest.getId());
            throw new SystemException(ResultCode.FAILED_UPDATE_ARTICLE);
        }
        log.info("帖子DB更新成功：articleId{}",updateArticleRequest.getId());
        //2. 更新redis
        //2.1 只更新title和updateTime
        Map<String, Object> updates = new HashMap<>();
        updates.put(FIELD_TITLE, updateArticleRequest.getTitle());
        updates.put(FIELD_UPDATE_TIME,now);
        redisTemplate.opsForHash().putAll(RedisKeyUtil.getArticleKey(updateArticleRequest.getId()),updates);
        //2.2 如果content存在那么删除，下次需要的时候再缓存
        Boolean delete = stringRedisTemplate.delete(RedisKeyUtil.getArticleContentKey(updateArticleRequest.getId()));
        if(!delete){
            log.error("从redis中删除文章 {} 内容失败",updateArticleRequest.getId());
        }
        log.info("帖子 redis 更新成功，articleId:{}",updateArticleRequest.getId());
        return true;
    }

    @Transactional
    @Override
    public boolean deleteArticle(Long articleId,Long boardId) {
        //鉴权由controller层负责
        //1. 先删除数据库
        //1.1 先检查记录是否存在
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            log.warn("删除帖子失败, 帖子不存在, id: {}", articleId);
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }
        if(article.getDeleteState() == 1){
            log.error("帖子已经被删除，id：{}",articleId);
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }
        // 执行删除操作
        if( articleMapper.update(new LambdaUpdateWrapper<Article>()
                .set(Article::getDeleteState, 1)
                .eq(Article::getId, articleId)) != 1){
            log.warn("删除文章失败, id: {}", articleId);
            throw new SystemException(ResultCode.FAILED_ARTICLE_DELETE);
        }
        //更新用户发帖数量
        userServiceImpl.updateOneArticleCountById(article.getUserId(),-1);

        //更新板块发帖数量
        boardServiceImpl.updateOneArticleCountById(article.getBoardId(),-1);

        //2. 删除redis缓存
        try {
            Long deleteInBoard = stringRedisTemplate.opsForZSet().remove(RedisKeyUtil.getBoardArticlesZSetKey(boardId),articleId.toString());
            if(deleteInBoard == null || deleteInBoard != 1){
                log.error("删除redis board中的article失败,articleId:{},boardId:{}",articleId,boardId);
            }
        Boolean delete = redisTemplate.delete(RedisKeyUtil.getArticleKey(articleId));
        if(!delete){
                log.error("删除redis articleMeta缓存失败,articleId:{}",articleId);
            }
        delete = stringRedisTemplate.delete(RedisKeyUtil.getArticleContentKey(articleId));
            if(!delete){
                log.error("删除redis articleMeta缓存失败,articleId:{}",articleId);
            }
        } catch (Exception e) {
            log.error("删除文章redis缓存失败,articleId:{}",articleId,e);
            throw new SystemException(ResultCode.FAILED_ARTICLE_DELETE);
        }
        //打印日志
        log.info("删帖成功,帖子id: {} ,用户id：{}, 板块id:{}",article.getId(), article.getUserId() ,article.getBoardId());
        return true;
    }
}
