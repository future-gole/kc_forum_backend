package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.request.ArticleReplyAddRequest;
import com.doublez.kc_forum.common.pojo.response.UserArticleResponse;
import com.doublez.kc_forum.common.pojo.response.ArticleReplyMetaCacheDTO;
import com.doublez.kc_forum.common.pojo.response.ViewArticleReplyResponse;
import com.doublez.kc_forum.common.utiles.RedisKeyUtil;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.mapper.ArticleReplyMapper;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.ArticleReply;
import com.doublez.kc_forum.service.IArticleReplyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.BoundZSetOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ArticleReplyServiceImpl implements IArticleReplyService{


    @Autowired
    private ArticleReplyMapper articleReplyMapper;

    @Autowired
    private UserServiceImpl userServiceImpl;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisAsyncPopulationService redisAsync;

    @Autowired
    private DBAsyncPopulationService dbAsync;

    // 定义一个常量作为空结果的占位符
    private static final String EMPTY_ARTICLE_REPLY_ID_PLACEHOLDER = "-1";
    // 定义空结果缓存的过期时间（15分钟）
    private static final long EMPTY_CACHE_TTL_MINUTES = 15;

    private static final String CHILDREN_FIELD = "childrenCount";
    @Override
    @Transactional
    public void createArticleReply(ArticleReplyAddRequest articleReplyAddRequest) {
        //todo replyUserId  replyId
        if(articleReplyAddRequest.getPostUserId() <= 0
                || articleReplyAddRequest.getArticleId() <= 0
                || !StringUtils.hasText(articleReplyAddRequest.getContent())) {
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
        }

        //1. 判断帖子是否正常
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, // 建议也查一下ID，方便日志记录
                        Article::getReplyCount,
                        Article::getState,
                        Article::getDeleteState)
                .eq(Article::getId,articleReplyAddRequest.getArticleId()));

        if(article == null) {
            log.warn(ResultCode.FAILED_ARTICLE_NOT_EXISTS.toString());
            //todo 可以做一个缓存穿透
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);//帖子不存在
        }
        if( article.getDeleteState() == 1 || article.getState() == 1) {
            log.warn(ResultCode.FAILED_ARTICLE_BANNED.toString());
            //todo 可以做一个缓存穿透
            throw new BusinessException(ResultCode.FAILED_ARTICLE_BANNED);//被删除或者禁言
        }
        //2. 类型转化
        ArticleReply articleReply = copyProperties(articleReplyAddRequest,ArticleReply.class);
        //2.1 插入articleReply
        articleReply.setCreateTime(LocalDateTime.now());
        if(articleReplyMapper.insert(articleReply) != 1) {
            log.error("回复贴新增失败，article:{}",article.getId());
            throw new SystemException(ResultCode.FAILED_REPLY_CREATE);
        }
        //异步更新帖子回复数量+1
        dbAsync.updateArticleReplyCount(articleReplyAddRequest.getArticleId(),1);
        //3. 更新redis
        //3.1 判断是否为子回复
        if(articleReply.getReplyId() != null && articleReply.getReplyId() > 0) {
            //1. 异步修改数据库 childrenCount数量
            Long replyId = articleReply.getReplyId();
            dbAsync.updateReplyChildrenCountInDb(replyId,1);
            //2. 同步修改redis
            updateReplyChildrenCountRedis(articleReply.getReplyId(),articleReply.getId(),articleReply.getCreateTime(),true);
        }else {
            //为顶级回复
            //3.2 让reply的ZSET缓存失效
            String key = RedisKeyUtil.getArticleTopRepliesZsetKey(articleReplyAddRequest.getArticleId());
            Boolean delete = redisTemplate.delete(key);
            if (!delete) {
                log.info("reply ZSET缓存删除失败或者已经被删除,article:{},key:{}", articleReplyAddRequest.getArticleId(), key);
            }
        }
        //3.3 更新redis中article中的回复数量
        redisTemplate.opsForHash().put(RedisKeyUtil.getArticleKey(articleReply.getArticleId()), "replyCount", article.getReplyCount() + 1);

        //打印日志
        log.info("回帖成功, 回帖id: {} 用户id：{} 帖子id: {}", articleReply.getId(), articleReply.getReplyUserId(), articleReply.getArticleId());
    }



    private void updateReplyChildrenCountRedis(Long parentReplyId, Long childrenReplyId, LocalDateTime createTime,boolean isCreate) {
        String repliesChildrenZsetKey = RedisKeyUtil.getRepliesChildrenZsetKey(parentReplyId);
        try {
            if(isCreate){
                //1. replyId 缓存到patent的replyZset中
                Boolean add = stringRedisTemplate.opsForZSet().add(repliesChildrenZsetKey, childrenReplyId.toString(), (double) createTime.toEpochSecond(ZoneOffset.UTC));
                if(add == null || !add){
                    log.error("新增子回复失败,key:{},replyId:{},id:{}",repliesChildrenZsetKey,parentReplyId,childrenReplyId);
                    throw new SystemException(ResultCode.FAILED_OPERATION_REDIS,"子回复缓存至redis失败");
                }
                //2. 更新 parentReplyId 的 childrenCount
                redisTemplate.opsForHash().increment(RedisKeyUtil.getArticleReplyKey(parentReplyId),CHILDREN_FIELD , 1);
            }else {
                //1. 去除patent的replyZset中的缓存
                Long remove = stringRedisTemplate.opsForZSet().remove(repliesChildrenZsetKey, childrenReplyId.toString());
                if(remove == null || remove == 0) {
                    log.error("删除子回复失败,key:{},replyId:{},id:{}",repliesChildrenZsetKey,parentReplyId,childrenReplyId);
                    throw new SystemException(ResultCode.FAILED_OPERATION_REDIS,"子回复缓存至redis失败");
                }
                //1. 更新 parentReplyId 的 childrenCount
                redisTemplate.opsForHash().increment(RedisKeyUtil.getArticleReplyKey(parentReplyId),CHILDREN_FIELD,-1);
            }
        } catch (RedisSystemException e) {
            log.error("redis 删除失败,Key:{},parentReplyId:{},childrenReplyId:{},createTime:{},isCreate:{}"
                    ,repliesChildrenZsetKey,parentReplyId,childrenReplyId,createTime,isCreate);
            throw new SystemException(ResultCode.FAILED_OPERATION_REDIS,e.getMessage());
        }
    }

    @Override
    public ViewArticleReplyResponse getArticleReply(Long articleId, Integer currentPage, Integer pageSize) {
        //1. 获取topKey
        String articleTopRepliesZsetKey = RedisKeyUtil.getArticleTopRepliesZsetKey(articleId);
        long start = (long) (currentPage - 1) * pageSize; // ZSETs are 0-indexed
        long end = start + pageSize - 1;
        //1.1 获取top回复总数
        BoundZSetOperations<String, String> zSetOps = stringRedisTemplate.boundZSetOps(articleTopRepliesZsetKey);
        Long totalCount = zSetOps.zCard();
        Set<String> topReplyIdStrings;
        //1.2 判空
        if (totalCount == null || totalCount == 0) {
            log.warn("文章 {} 的顶级回复缓存未命中，从数据库加载.", articleId);
            //1.2.1 查询数据库
            List<ArticleReply> dbTopReplies = loadAndCacheTopRepliesFromDB(articleId,articleTopRepliesZsetKey);
            if (dbTopReplies.isEmpty()) return new ViewArticleReplyResponse(null,0L);
            totalCount = (long)dbTopReplies.size();
            // 判断分页请求是否超出范围
            if (start >= totalCount)
                return new ViewArticleReplyResponse(null,0L);

            //1.2.2 存在,类型转化
            List<ArticleReplyMetaCacheDTO> records = new ArrayList<>();
            for (ArticleReply articleReply : dbTopReplies) {
                records.add(copyProperties(articleReply,ArticleReplyMetaCacheDTO.class));
            }
            setReplyUserAndCache(records);
            //返回
            return new ViewArticleReplyResponse(records,totalCount);
        } else if (start >= totalCount) {
            log.info("请求的文章 {} 顶级回复页码 {} 超出范围 (总数: {}).", articleId, currentPage, totalCount);
            return new ViewArticleReplyResponse(null,0L);
        }
        //2. 查询redis
        topReplyIdStrings = zSetOps.reverseRange(start, end);
        if(topReplyIdStrings == null || topReplyIdStrings.isEmpty()) {
            log.info("在ZSET {} 中未找到文章 {} 第 {} 页的回复贴", articleTopRepliesZsetKey, articleId, currentPage);
            return new ViewArticleReplyResponse(null,0L);
        }
        //2.1 判断缓存穿透
        if(topReplyIdStrings.size() == 1 && topReplyIdStrings.contains(EMPTY_ARTICLE_REPLY_ID_PLACEHOLDER)){
            log.warn("从ZSET {} 中获取到缓存穿透占位符，文章 {} 无回复贴",articleTopRepliesZsetKey, articleId);
            return new ViewArticleReplyResponse(null,0L);
        }
        //最终的回复元数据
        List<ArticleReplyMetaCacheDTO> resultList = new ArrayList<>();

        List<Long> topReplyIdInOrder = topReplyIdStrings.stream().map(Long::parseLong).toList();
        getArticleReplyFromRedis(resultList,topReplyIdInOrder,true);

        //去重+ 填写用户字段
        setReplyUserAndCache(resultList);
        //返回
        log.info("查询回复贴成功,article:{}",articleId);
        return new ViewArticleReplyResponse(resultList,totalCount);
        //todo 获取回复下的预览信息
    }

    private void setReplyUserAndCache(List<ArticleReplyMetaCacheDTO> records) {
        //1.2.3 添加用户信息
        //去重用户
        List<Long> userIds = records.stream()
                .map(ArticleReplyMetaCacheDTO::getReplyUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserArticleResponse> userMap = userServiceImpl.fetchAndCacheUsers(userIds);
        //组装
        for(ArticleReplyMetaCacheDTO topRely : records) {
            topRely.setUser(userMap.get(topRely.getReplyUserId()));
        }
    }

    private List<ArticleReply> loadAndCacheTopRepliesFromDB(Long articleId,String ArticleTopRepliesZsetKey) {
        List<ArticleReply> dbTopReplies = articleReplyMapper.selectList(new LambdaQueryWrapper<ArticleReply>()
                .eq(ArticleReply::getArticleId, articleId)
                .and(qw -> qw.isNull(ArticleReply::getReplyId).or().eq(ArticleReply::getReplyId, 0L))
                .eq(ArticleReply::getDeleteState, 0).eq(ArticleReply::getState, 0)
                .orderByDesc(ArticleReply::getCreateTime));

        if (dbTopReplies.isEmpty()) {
            //进行缓存穿透
            stringRedisTemplate.opsForZSet().add(ArticleTopRepliesZsetKey, EMPTY_ARTICLE_REPLY_ID_PLACEHOLDER, 0.0);
            stringRedisTemplate.expire(ArticleTopRepliesZsetKey, EMPTY_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return Collections.emptyList();
        }
        //缓存回复元数据
        redisAsync.cacheArticleReplyList(dbTopReplies.stream().map(a->copyProperties(a,ArticleReplyMetaCacheDTO.class)).toList());
        //缓存回复zset
        redisAsync.articleZsetFromDBToRedis(RedisKeyUtil.getArticleTopRepliesZsetKey(articleId),dbTopReplies);
        return dbTopReplies;
    }

    /**
     * 从redis中获取reply缓存数据，有缺少异步回填缓存
     * @param resultList
     * @param ReplyIdInOrder
     */
    private void getArticleReplyFromRedis(List<ArticleReplyMetaCacheDTO> resultList,List<Long> ReplyIdInOrder,boolean isTopReplies) {
        List<Object> rawHshObjectList  = redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (Long replyId : ReplyIdInOrder) { // 按照有序ID列表的顺序请求
                        String repliesMetaKey = RedisKeyUtil.getArticleReplyKey(replyId);
                        connection.hashCommands().hGetAll(repliesMetaKey.getBytes(StandardCharsets.UTF_8));
                    }
                    return null;
        });
        //最后的结果
        Map<Long,ArticleReplyMetaCacheDTO> foundRepliesMap = new HashMap<>();
        List<Long> missedReplyIds = new ArrayList<>();
        //处理Pipeline的结果，区分命中和未命中
        for (int i = 0; i < ReplyIdInOrder.size(); i++) {
            Long currentId = ReplyIdInOrder.get(i);
            Object rawHashObject = (rawHshObjectList != null && i < rawHshObjectList.size()) ? rawHshObjectList.get(i) : null;

            if(rawHashObject != null) {
                ArticleReplyMetaCacheDTO reply = objectMapper.convertValue(rawHashObject, ArticleReplyMetaCacheDTO.class);
                if (reply.getId() != null) {
                    foundRepliesMap.put(currentId, reply); // 存入map，键是ID
                } else {
                    log.warn("文章回复数据从Redis Hash映射失败 (或映射结果ID为null), articleId: {}", currentId);
                    missedReplyIds.add(currentId);
                }
            }
        }
        // 5. 从数据库批量获取未命中的文章回复并回填缓存
        if (!missedReplyIds.isEmpty()) {
            log.info("文章回复缓存未命中，将从数据库查询，ID列表: {}", missedReplyIds);
            // 确保数据库查询也考虑了 deleteState
            List<ArticleReply> dbReplies = articleReplyMapper.selectList(new LambdaQueryWrapper<ArticleReply>()
                    .in(ArticleReply::getId, missedReplyIds)
                    .eq(ArticleReply::getDeleteState, 0)); // 确保回复未被删除

            List<ArticleReplyMetaCacheDTO> metaReplies = dbReplies.stream().map(a -> copyProperties(a, ArticleReplyMetaCacheDTO.class)).toList();
            for (ArticleReplyMetaCacheDTO metaReply : metaReplies) {
                if (metaReply != null && metaReply.getId() != null) {
                    foundRepliesMap.put(metaReply.getId(), metaReply); // 将从DB获取的数据也放入map
                    log.info("从数据库获取文章回复 {} 成功，已回填缓存", metaReply.getId());
                }
            }
            // 异步回填缓存
            redisAsync.cacheArticleReplyList(metaReplies);
        }
        // 重新按照 ReplyIdInOrder 的顺序填充 resultList
        for (Long replyId : ReplyIdInOrder) {
            ArticleReplyMetaCacheDTO reply = foundRepliesMap.get(replyId);
            if (reply != null) {
                resultList.add(reply);
                //如果子回复还有子回复，直接递归查询
                if(!isTopReplies && reply.getChildrenCount() > 0){
                    List<ArticleReplyMetaCacheDTO> childrenReplyByReplyId = getChildrenReplyByReplyId(reply.getId(), 0, 100);
                    resultList.addAll(childrenReplyByReplyId);
                }
            } else {
                log.warn("ReplyIdInOrder中的回复ID: {} 未在缓存或数据库中找到，将跳过。", replyId);
            }
        }
    }

    @Override
    public List<ArticleReplyMetaCacheDTO> getChildrenReplyByReplyId(Long replyId,Integer currentPage, Integer pageSize) {
        String ChildrenKey = RedisKeyUtil.getRepliesChildrenZsetKey(replyId);
        long start = (long) (currentPage - 1) * pageSize;
        long end = start + pageSize - 1;

        //获取zset中的childrenReplyId
        Set<String> keys = stringRedisTemplate.opsForZSet().reverseRange(ChildrenKey, start, end);
        if(keys == null || keys.isEmpty()){
            log.info("在ZSET {} 中未找到父回复 {} 第 {} 页的回复贴，从数据库中加载", ChildrenKey, replyId, currentPage);
            //查询数据库
            List<ArticleReply> dbChildrenReplies = loadAndCacheChildrenRepliesFromDB(replyId);
            //为空直接返回
            if(dbChildrenReplies == null) return null;

            //1.2.2 存在,类型转化
            List<ArticleReplyMetaCacheDTO> records = new ArrayList<>();
            for (ArticleReply articleReply : dbChildrenReplies) {
                if(articleReply.getChildrenCount() > 0){
                    List<ArticleReplyMetaCacheDTO> childrenReplyByReplyId = getChildrenReplyByReplyId(articleReply.getId(), 0, 100);
                    records.addAll(childrenReplyByReplyId);
                }
                records.add(copyProperties(articleReply,ArticleReplyMetaCacheDTO.class));
            }
            setReplyUserAndCache(records);
            //返回
            return records;
        }
        //todo 缓存穿透
        List<ArticleReplyMetaCacheDTO> resultList = new ArrayList<>();
        List<Long> childrenReplyIdsInOrder = keys.stream().map(Long::parseLong).toList();

        getArticleReplyFromRedis(resultList, childrenReplyIdsInOrder,false);

        //去重+ 填写用户字段
        setReplyUserAndCache(resultList);
        //返回
        log.info("查询子回复贴成功,replyId:{}",replyId);
        return resultList;
    }

    private List<ArticleReply> loadAndCacheChildrenRepliesFromDB(Long replyId) {
        List<ArticleReply> dbChildrenReplies = articleReplyMapper.selectList(new LambdaQueryWrapper<ArticleReply>()
                .eq(ArticleReply::getReplyId, replyId)
                .eq(ArticleReply::getDeleteState, 0)
                .orderByDesc(ArticleReply::getCreateTime));

        //todo缓存穿透
        //缓存回复元数据
        redisAsync.cacheArticleReplyList(dbChildrenReplies.stream().map(a -> copyProperties(a, ArticleReplyMetaCacheDTO.class)).toList());
        //缓存回复zset
        //先判空
        if(dbChildrenReplies.isEmpty()) return null;
        redisAsync.replyZsetFromDBToRedis(RedisKeyUtil.getRepliesChildrenZsetKey(replyId), dbChildrenReplies);
        log.info("从数据库中加载子回复贴完成，replyId:{}",replyId);
        return dbChildrenReplies;
    }


    @Transactional
    @Override
    public int deleteArticleReply(Long userId,Long articleReplyId,Long articleId) {
        //1. 删除数据库
        int row = articleReplyMapper.update(new LambdaUpdateWrapper<ArticleReply>()
                .set(ArticleReply::getDeleteState,1)
                .eq(ArticleReply::getReplyUserId,userId)
                .eq(ArticleReply::getArticleId,articleId)
                .eq(ArticleReply::getId,articleReplyId));
        if(row != 1) {
            throw new SystemException(ResultCode.FAILED_REPLY_DELETE);
        }
        log.info("回复贴数据库删除, articleReplyId:{}",articleReplyId);
        //减少帖子数量
        //异步更新帖子回复数量-1
        dbAsync.updateArticleReplyCount(articleId,-1);
        //2. 删除redis缓存
        //2.1 先判断是不是子回复
        ArticleReply partentReply = articleReplyMapper.selectOne(new LambdaQueryWrapper<ArticleReply>()
                .select(ArticleReply::getReplyId)//获取可能的顶级回复id
                .eq(ArticleReply::getId, articleReplyId));
        Long parentReplyId = partentReply.getReplyId();
        if(parentReplyId != null && parentReplyId > 0){
            //为子回复
            //2.1.1 同步更新redis的值
            updateReplyChildrenCountRedis(parentReplyId,articleReplyId,LocalDateTime.now(),false);
            //2.1.2 异步更新数据库中顶级回复的ChildrenCount，-1
            dbAsync.updateReplyChildrenCountInDb(parentReplyId,-1);
        }else {
            //为顶级回复
            //2.2 删除顶级回复zset中的缓存
            String articleTopRepliesZsetKey = RedisKeyUtil.getArticleTopRepliesZsetKey(articleId);

            Long remove = stringRedisTemplate.opsForZSet().remove(articleTopRepliesZsetKey, articleReplyId.toString());
            if(remove == null){
                log.error("删除回复贴的zset缓存失败,key:{},articleReplyId:{},article:{}",articleTopRepliesZsetKey,articleReplyId,articleId);
                throw new SystemException(ResultCode.FAILED_REPLY_DELETE);
            }
            //2.3 删除reply的缓存
            String articleTopRepliesKey = RedisKeyUtil.getArticleReplyKey(articleReplyId);
            Boolean delete = redisTemplate.delete(articleTopRepliesKey);
            if(!delete){
                log.error("删除回复贴元数据失败,key:{},articleReplyId:{},article:{}",articleTopRepliesKey,articleReplyId,articleId);
                throw new SystemException(ResultCode.FAILED_REPLY_DELETE);
            }
        }
        return row;
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
