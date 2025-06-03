package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.request.UpdateArticleRequest;
import com.doublez.kc_forum.common.pojo.response.ArticleDetailResponse;
import com.doublez.kc_forum.common.pojo.response.UserArticleResponse;
import com.doublez.kc_forum.common.pojo.response.ViewArticlesResponse;
import com.doublez.kc_forum.common.utiles.AssertUtil;
import com.doublez.kc_forum.mapper.ArticleMapper;
import com.doublez.kc_forum.model.Article;
import com.doublez.kc_forum.model.Board;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.IArticleService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArticleServiceImpl implements IArticleService {

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private BoardServiceImpl boardServiceImpl;//注意不可用造成循环引用

    @Autowired
    private UserServiceImpl userServiceImpl;


    @Override
    public Long getUserId(Long articleId) {
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>().select(Article::getUserId)
                .eq(Article::getId, articleId));
        if (article == null) {
            log.warn("文章不存在，无法查询对应用户id");
            throw new BusinessException(ResultCode.FAILED_ARTICLE_NOT_EXISTS);
        }
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
        //赋予默认值,好像不需要这样，数据库的性能更快
//        article.setLikeCount(0);
//        article.setVisitCount(0);
//        article.setReplyCount(0);
//        article.setState((byte)0);
//        article.setDeleteState((byte)0);
//        article.setCreateTime(LocalDateTime.now());

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

        //打印日志
        log.info("发帖成功, 帖子id: {}, 用户id：{} ,板块id:{} " ,article.getId() ,article.getUserId(),article.getBoardId());

    }

    @Override
    public List<ViewArticlesResponse> getAllArticlesByBoardId(@RequestParam(required = false) Long id) {
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

        List<ViewArticlesResponse> viewArticlesResponse = articles.stream().map(article -> {
            User user = userMap.get(article.getUserId());
            //判断用户是否存在
            AssertUtil.checkClassNotNull(user,ResultCode.FAILED_USER_NOT_EXISTS,article.getId());

            UserArticleResponse userArticleResponse = copyProperties(user, UserArticleResponse.class);
            ViewArticlesResponse viewArticleResponse = copyProperties(article, ViewArticlesResponse.class);

            viewArticleResponse.setUser(userArticleResponse);

            return viewArticleResponse;
        }).collect(Collectors.toList());

        log.info("{}:查询帖子成功", ResultCode.SUCCESS.getMessage());
        return viewArticlesResponse;
    }

    @Override
    public ArticleDetailResponse getArticleDetailById(Long userId, Long id) {
        //查询article
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>().eq(Article::getId,id)
                .eq(Article::getDeleteState, 0).eq(Article::getState, 0));

        AssertUtil.checkClassNotNull(article,ResultCode.FAILED_ARTICLE_NOT_EXISTS,id);
        //查询user
        User user = userServiceImpl.selectUserInfoById(article.getUserId());

        AssertUtil.checkClassNotNull(user,ResultCode.FAILED_USER_NOT_EXISTS,article.getUserId());

        //组装
        UserArticleResponse userArticleResponse =copyProperties(user, UserArticleResponse.class);
        ArticleDetailResponse articleDetailResponse = copyProperties(article, ArticleDetailResponse.class);

        articleDetailResponse.setUser(userArticleResponse);

        //判断是否是本人帖子，用于设置权限
        if(userId.equals(article.getUserId())){
            articleDetailResponse.setOwn(true);
        }

        //访问量加1
        if(articleMapper.update(new LambdaUpdateWrapper<Article>().set(Article::getVisitCount,article.getVisitCount()+1)
                .eq(Article::getId,id).eq(Article::getDeleteState, 0).eq(Article::getState, 0)) != 1){
            log.error(ResultCode.ERROR_SERVICES+": 访问量新增异常");
            throw new SystemException(ResultCode.ERROR_SERVICES);
        }
        //更新返回给前端的帖子访问次数
        articleDetailResponse.setVisitCount(articleDetailResponse.getVisitCount()+1);

        log.info("查询帖子细节成功, articleId:{}",id);
        return articleDetailResponse;
    }

    @Override
    public List<ViewArticlesResponse> getAllArticlesByUserId(Long userId) {
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
        List<ViewArticlesResponse> viewArticlesResponses = articles.stream().map(article -> {
            ViewArticlesResponse viewArticlesResponse = copyProperties(article, ViewArticlesResponse.class);
            viewArticlesResponse.setUser(userArticleResponse);

            return viewArticlesResponse;
        }).toList();
        log.info("查询用户发布帖子成功：userId:{}",userId);
        return viewArticlesResponses;
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
