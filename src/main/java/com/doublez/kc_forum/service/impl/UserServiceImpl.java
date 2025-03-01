package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.pojo.request.RegisterRequest;
import com.doublez.kc_forum.common.pojo.request.UserLoginRequest;
import com.doublez.kc_forum.common.pojo.response.UserLoginResponse;
import com.doublez.kc_forum.common.utiles.JwtUtil;
import com.doublez.kc_forum.common.utiles.SecurityUtil;
import com.doublez.kc_forum.mapper.EmailVerificationMapper;
import com.doublez.kc_forum.mapper.UserMapper;
import com.doublez.kc_forum.model.EmailVerification;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailVerificationMapper verificationMapper;

    @Value("${upload.avatar-base-path}")
    private String avatarBasePath;

    @Value("${upload.avatar-base-url}")
    private String avatarBaseUrl;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAvatar(Long userId, MultipartFile file) {
        try {
            // 1. 校验文件类型
            String originalFilename = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFilename);
            if (!isValidImageType(fileExtension)) {
                throw new ApplicationException(Result.failed(ResultCode.INVALID_FILE_TYPE));
            }

            // 2. 生成唯一文件名
            String uniqueFileName = "avatar_" + userId + "_" + UUID.randomUUID().toString() + "." + fileExtension;

            // 3. 构建存储路径（按用户ID分目录）
            String relativePath = "/avatars/" + userId + "/";
            Path directoryPath = Paths.get(avatarBasePath, relativePath);

            // 4. 创建目录
            Files.createDirectories(directoryPath);

            // 5. 完整文件路径
            Path filePath = Paths.get(avatarBasePath, relativePath, uniqueFileName);

            // 6. 保存文件
            Files.write(filePath, file.getBytes());

            // 7. 更新用户表中的 avatar_url 字段
            String avatarUrl = relativePath + uniqueFileName;
            User user = new User();
            user.setId(userId);
            user.setAvatarUrl(avatarUrl);
            userMapper.updateById(user);

            // 8. 返回完整的头像URL
            return avatarBaseUrl + avatarUrl;

        } catch (IOException e) {
            log.error("用户 {} 上传头像失败", userId, e);
            throw new ApplicationException(Result.failed(ResultCode.UPLOAD_FAILED));
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
    public User selectUserInfoByUserName(String email) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));

        if (user == null) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        return user;
    }

    @Transactional
    @Override
    public Result createNormalUser(RegisterRequest registerRequest) {
        //判断用户名是否存在,名字有重名的可能，可以转为邮箱
        // 检查邮箱验证状态
        EmailVerification verification = verificationMapper.selectOne(new LambdaQueryWrapper<EmailVerification>()
                .eq(EmailVerification::getEmail, registerRequest.getEmail()));

        if (verification == null || !verification.getVerified()) {
            return Result.failed(ResultCode.ERROR_EMAIL_NOT_VERIFIED);
        }
        //!!!!不能注入，要创建新的对象
        User user = new User();
        //密码加密
        registerRequest.setPassword(SecurityUtil.encrypt(registerRequest.getPassword()));
        //类型转化
        try {
            BeanUtils.copyProperties(registerRequest, user);
        } catch (BeansException e) {
            log.error(ResultCode.ERROR_TYPE_CHANGE.toString());
            throw new ApplicationException(Result.failed(ResultCode.ERROR_TYPE_CHANGE));
        }

        //新增用户默认值用代码控制
        //TODO。。。
        int result = userMapper.insert(user);

        if(result != 1) {
            //打印日志
            log.info(ResultCode.FAILED_CREATE.toString());
            //抛异常
            throw new ApplicationException(Result.failed(ResultCode.FAILED_CREATE));
        }
        //打印日志
        log.info("新增用户成功,username:" + user.getUserName());
        return Result.sucess();
    }

    @Override
    public UserLoginResponse login(UserLoginRequest loginRequest) {
        //判断用户是否存在,查询信息
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, loginRequest.getEmail())
                .eq(User::getDeleteState,0));
        //用户不存在
        if(user == null){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        //用户密码错误
        if(!SecurityUtil.checkPassword(loginRequest.getPassword(), user.getPassword())){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_LOGIN));
        }
        //密码正确
        UserLoginResponse loginResponse = new UserLoginResponse();
        loginResponse.setUserId(user.getId());
        //放入载荷
        Map<String,Object> map = new HashMap<>();
        map.put("email",loginRequest.getEmail());
        map.put("Id", user.getId());
        loginResponse.setAuthorization(JwtUtil.getToken(map));
        return loginResponse;

    }

    @Override
    public User selectUserInfoById(Long id) {
        //判断用户是否存在
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, id).eq(User::getDeleteState,0));
        //不存在
        if (user == null) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
        }
        log.info("查询用户成功");
        return user;
    }

    /**
     * 根据id修改该用户文章数量
     * @param id
     */
    @Override
    public void updateOneArticleCountById(Long id,int increment) {
        if(id == null || id <= 0 ) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        // 直接更新，利用数据库原子操作避免并发问题
        int rows = userMapper.update( new LambdaUpdateWrapper<User>()
                .setSql("article_count = article_count + " + increment) // 确保字段名与数据库一致
                .eq(User::getId, id)
                .eq(User::getState,0)//判断是否被禁言
                .eq(User::getDeleteState, 0)
        );
        if (rows != 1) {
            // 可能原因：用户不存在、已删除或计数未变化
            log.warn("更新用户发帖数量失败, userId: {}", id);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
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
    public boolean  modifyUserInfoById(User user) {
        if(user == null || user.getEmail() == null){
            log.warn(ResultCode.FAILED_USER_NOT_EXISTS.toString());

            return false;
        }
        //用邮箱作为唯一校验？？？
        if(userMapper.selectOne(new LambdaQueryWrapper<User>()
                .select(User::getId)
                .eq(User::getEmail,user.getEmail())) == null){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_NOT_EXISTS));
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
            log.error("{}: id = {}",user.getId(),ResultCode.FAILED_MODIFY_USER.getMessage());
            throw new ApplicationException(Result.failed(ResultCode.FAILED_MODIFY_USER));
        }
        return true;
    }

    @Transactional
    @Override
    public void modifyUserInfoPasswordById(String password,Long id) {
        //判断
        if(id == null || id <= 0){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        //密码需要加密
        password = SecurityUtil.encrypt(password);
        if(userMapper.update(new LambdaUpdateWrapper<User>()
                .set(User::getPassword,password)
                .eq(User::getId,id)
                .eq(User::getDeleteState,0)) != 1) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_MODIFY_USER));
        }

    }
    @Transactional
    @Override
    public Result modifyUserInfoEmailById(String email, Long id) {
        //判断
        if(id == null || id <= 0){
            throw new ApplicationException(Result.failed(ResultCode.FAILED_PARAMS_VALIDATE));
        }
        //验证邮箱状态
        // 检查邮箱验证状态
        EmailVerification verification = verificationMapper.selectOne(new LambdaQueryWrapper<EmailVerification>()
                .eq(EmailVerification::getEmail, email));

        if (verification == null || !verification.getVerified()) {
            return Result.failed(ResultCode.ERROR_EMAIL_NOT_VERIFIED);
        }

        if(userMapper.update(new LambdaUpdateWrapper<User>()
                .set(User::getEmail,email)
                .eq(User::getId,id)
                .eq(User::getDeleteState,0)) != 1) {
            throw new ApplicationException(Result.failed(ResultCode.FAILED_MODIFY_USER));
        }
        return Result.sucess();
    }

}
