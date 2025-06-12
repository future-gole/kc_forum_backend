package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.common.exception.SystemException;
import com.doublez.kc_forum.common.pojo.request.RegisterRequest;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserArticleResponse;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.common.utiles.RedisKeyUtil;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.mapper.UserMapper;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.IUserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.doublez.kc_forum.common.utiles.AssertUtil.copyProperties;

@Service
@Slf4j
public class UserServiceImpl implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailServiceImpl emailServiceImpl;

    @Value("${upload.avatar-base-path}")
    private String avatarBasePath;

    @Value("${upload.avatar-base-url}")
    private String avatarBaseUrl;

    @Autowired
    private RefreshTokenService refreshTokenService;
    @Value("${jwt.refresh-token.expiration-ms}")
    private long refreshTokenExpirationMillis;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisAsyncPopulationService redisAsync;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(Long userId, MultipartFile file) {
        try {
            // 1. 校验文件类型
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            if (!isValidImageType(fileExtension)) {
                log.warn("图片文件后缀不合法：{}",originalFilename);
                throw new BusinessException(ResultCode.INVALID_FILE_TYPE);
            }

            // 2. 生成唯一文件名
            String uniqueFileName = "avatar_" + userId + "_" + UUID.randomUUID() + "." + fileExtension;

            // 3. 构建存储路径（按用户ID分目录）
            String relativePath = "/avatars/" + userId + "/";
            Path directoryPath = Paths.get(avatarBasePath, relativePath);

            // 4. 创建目录
            Files.createDirectories(directoryPath);

            // 5. 完整文件路径
            Path filePath = Paths.get(avatarBasePath, relativePath, uniqueFileName);

            // 6. 查询旧头像的 URL
            User existingUser = userMapper.selectById(userId);
            String oldAvatarUrl = (existingUser != null) ? existingUser.getAvatarUrl() : null;

            // 7. 保存新文件
            Files.write(filePath, file.getBytes());

            // 8. 更新用户表中的 avatar_url 字段
            String avatarUrl = relativePath + uniqueFileName;
            User user = new User();
            user.setId(userId);
            user.setAvatarUrl(avatarUrl);
            userMapper.updateById(user);

            // 9. 删除旧头像 (如果存在)
            if (oldAvatarUrl != null && !oldAvatarUrl.isEmpty()) {
                Path oldAvatarPath = Paths.get(avatarBasePath, oldAvatarUrl);
                try {
                    Files.deleteIfExists(oldAvatarPath); // 尝试删除，如果不存在也不会报错
                } catch (IOException e) {
                    // 旧头像删除失败，记录日志，但不影响新头像上传
                    log.error("删除旧头像失败: {},userId:{}", oldAvatarPath,userId,e);
                }
            }
            //直接删除对应的redis缓存，下次查询到了在进行缓存
            Boolean delete = stringRedisTemplate.delete(RedisKeyUtil.getUserResponseKey(userId));
            //出错了也不抛出异常
            if(!delete){
                log.warn("头像对应缓存删除失败,key不存在或者删除失败：userId:{}",userId);
            }
            // 10. 返回相对的头像URL即可!!! nginx自动会补充
            return avatarUrl;

        } catch (IOException e) {
            log.error("用户 {} 上传头像失败", userId, e);
            throw new SystemException(ResultCode.UPLOAD_AVATAR_FAILED);
        }
    }

    // 校验文件类型是否为图片
    private boolean isValidImageType(String fileExtension) {
        String lowerCaseExtension = fileExtension.toLowerCase();
        return lowerCaseExtension.equals("jpg") || lowerCaseExtension.equals("jpeg") ||
                lowerCaseExtension.equals("png") || lowerCaseExtension.equals("gif");
    }

    // 获取文件扩展名
    private String getFileExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".") + 1);
        } else {
            return "";
        }
    }

    @Override
    public User selectUserInfoByUserEmail(String email) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));

        if (user == null) {
            throw new BusinessException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        return user;
    }

    @Transactional
    @Override
    public Result<?> createNormalUser(RegisterRequest registerRequest) {
        // 检查邮箱验证状态
        emailServiceImpl.verifyEmail(registerRequest.getEmail(),registerRequest.getCode());

        //!!!!不能注入，要创建新的对象
        User user = new User();
        //密码加密
        registerRequest.setPassword(SecurityUtil.encrypt(registerRequest.getPassword()));
        //类型转化
        try {
            BeanUtils.copyProperties(registerRequest, user);
        } catch (BeansException e) {
            log.error(ResultCode.ERROR_TYPE_CHANGE.toString());
            throw new BusinessException(ResultCode.ERROR_TYPE_CHANGE);
        }

        //新增用户默认值用代码控制
        //TODO。。。
        int result = userMapper.insert(user);

        if(result != 1) {
            //打印日志
            log.warn(ResultCode.FAILED_CREATE.toString());
            //抛异常
            throw new BusinessException(ResultCode.FAILED_CREATE);
        }
        //打印日志
        log.info("新增用户成功,username:{}",user.getUserName());
        return Result.success();
    }

    @Override
    public UserLoginResponse login(UserLoginRequest loginRequest, HttpServletResponse response) {
        //判断用户是否存在,查询信息
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, loginRequest.getEmail())
                .eq(User::getDeleteState,0));
        //用户不存在
        if(user == null){
            throw new BusinessException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        //用户密码错误
        if(!SecurityUtil.checkPassword(loginRequest.getPassword(), user.getPassword())){
            throw new BusinessException(ResultCode.FAILED_LOGIN);
        }
        //密码正确
        UserLoginResponse loginResponse = new UserLoginResponse();
        loginResponse.setUserId(user.getId());
        //放入载荷
        Map<String,Object> accessTokenClaims  = new HashMap<>();
        accessTokenClaims .put("email",loginRequest.getEmail());
        accessTokenClaims .put("Id", user.getId());
        String accessToken = JwtUtil.genToken(accessTokenClaims);

        // --- 生成 Refresh Token (随机字符串) ---
        String refreshTokenString = UUID.randomUUID().toString();

        //放入 认证的jwt
        loginResponse.setAuthorization(accessToken);
        // --- !! 设置 Refresh Token 到 HttpOnly Cookie !! ---
        Cookie refreshTokenCookie = new Cookie("refreshToken", refreshTokenString); // Cookie 名称为 "refreshToken"
        refreshTokenCookie.setHttpOnly(true); // !! 关键：设置为 HttpOnly !!
        refreshTokenCookie.setSecure(true); // !! 关键：仅在 HTTPS 下传输 (生产环境必须 true) !!
        refreshTokenCookie.setPath("/api/token"); // !! 关键：设置 Cookie 的路径，通常是刷新接口的路径或更上层路径 !!
        refreshTokenCookie.setMaxAge((int) (refreshTokenExpirationMillis / 1000)); // 设置 Cookie 过期时间 (秒)
        // refreshTokenCookie.setDomain("yourdomain.com"); // (可选) 生产环境设置域名
        // SameSite 策略: Strict 最严格，Lax 常用平衡点。防止 CSRF。
        // 注意: 在 Spring Boot 2.x/3.x 中，可以通过 application.yml 或配置类统一设置 SameSite
        // response.setHeader("Set-Cookie", refreshTokenCookie.toString() + "; SameSite=Strict"); // 手动添加 SameSite
        // 或者使用 Spring 提供的 ResponseCookie Builder (更推荐)
        // ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshTokenString)
        //         .httpOnly(true)
        //         .secure(true) // 生产环境设置为 true
        //         .path("/api/token") // 限制 Cookie 路径
        //         .maxAge(Duration.ofMillis(refreshTokenExpirationMillis))
        //         .sameSite("Strict") // "Lax" 或 "Strict"
        //         // .domain("yourdomain.com") // 生产环境设置
        //         .build();
        // response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        response.addCookie(refreshTokenCookie); // 添加 Cookie 到响应

        //添加refreshToken到redis
        refreshTokenService.createRefreshToken(refreshTokenString);
        return loginResponse;
    }

    @Override
    public User selectUserInfoById(Long id) {
        //判断用户是否存在
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, id).eq(User::getDeleteState,0));
        //不存在
        if (user == null) {
            throw new BusinessException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        log.info("查询用户成功,用户id:{}",id);
        return user;
    }

    /**
     * 根据id修改该用户文章数量
     * @param id
     */
    @Override
    public void updateOneArticleCountById(Long id,int increment) {
        if(id == null || id <= 0 ) {
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        // 直接更新，利用数据库原子操作避免并发问题
        int rows = userMapper.update( new LambdaUpdateWrapper<User>()
                .setSql("article_count = article_count + " + increment) // 确保字段名与数据库一致
                .eq(User::getId, id)
                .eq(User::getState,0)//判断是否被禁言
                .eq(User::getDeleteState, 0)
        );
        if (rows != 1) {
            // 计数未变化,是否被禁言或者被删除在controller层已经初步校验过了
            log.warn("更新用户发帖数量失败, userId: {}", id);
            throw new SystemException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        log.info("用户：文章数量更新");
    }

    /**
     * 批量提取用户id
     * @param userIds
     * @return Map<Long, User>
     */
    public Map<Long, User> selectUserInfoByIds(List<Long> userIds) {
        // 3. 批量查询 User 信息
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    @Transactional
    @Override
    public boolean modifyUserInfoById(User user) {
        if(user == null || user.getEmail() == null){
            log.warn(ResultCode.FAILED_USER_NOT_EXISTS.toString());
            throw new BusinessException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        if(userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getId,user.getId())) == null){
            log.warn(ResultCode.FAILED_USER_NOT_EXISTS.toString());
            throw new BusinessException(ResultCode.FAILED_USER_NOT_EXISTS);
        }
        //。。。补充完整校验
        //改进，判断有变化再改，如果都没变化就可以不用改
        int row = userMapper.update(new LambdaUpdateWrapper<User>()
                .set(User::getUserName,user.getUserName())
                .set(User::getNickName, user.getNickName())
                .set(User::getGender,user.getGender())
                .set(User::getPhone,user.getPhone())
                .set(User::getRemark,user.getRemark())
                .eq(User::getId, user.getId()));
        if(row != 1) {
            log.error("用户 {} 信息更新失败",user.getId());
            throw new SystemException(ResultCode.FAILED_MODIFY_USER);
        }
        return true;
    }

    @Transactional
    @Override
    public void modifyUserInfoPasswordById(String password,Long id) {
        //判断
        if(id == null || id <= 0){
            log.warn(ResultCode.FAILED_USER_NOT_EXISTS.toString());
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        //密码需要加密
        password = SecurityUtil.encrypt(password);
        if(userMapper.update(new LambdaUpdateWrapper<User>()
                .set(User::getPassword,password)
                .eq(User::getId,id)
                .eq(User::getDeleteState,0)) != 1) {
            log.error("用户 {} 更新密码失败",id);
            throw new SystemException(ResultCode.FAILED_MODIFY_USER);
        }

    }
    @Transactional
    @Override
    public Result<?> modifyUserInfoEmailById(String email, Long id,String code) {
        //判断
        if(id == null || id <= 0){
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE);
        }
        //验证邮箱
        emailServiceImpl.verifyEmail(email,code);

        //todo需要检查邮箱是否被占用
        if(userMapper.update(new LambdaUpdateWrapper<User>()
                .set(User::getEmail,email)
                .eq(User::getId,id)
                .eq(User::getDeleteState,0)) != 1) {
            log.error("用户 {} 更新邮箱失败",id);
            throw new SystemException(ResultCode.FAILED_MODIFY_USER);
        }
        return Result.success();
    }

    /**
     * 获取并缓存用户信息。
     */
    public Map<Long, UserArticleResponse> fetchAndCacheUsers(List<Long> userIds) {
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

            Map<Long,User> dbUsers = selectUserInfoByIds(missedUserIds);
            for (User dbUser : dbUsers.values()) {
                UserArticleResponse uar = copyProperties(dbUser, UserArticleResponse.class);
                userMap.put(dbUser.getId(), uar);
                redisAsync.cacheUser(uar);
            }
        }
        return userMap;
    }
}
