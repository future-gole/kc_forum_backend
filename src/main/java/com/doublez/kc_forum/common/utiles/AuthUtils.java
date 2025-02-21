package com.doublez.kc_forum.common.utiles;



import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.impl.UserServiceImpl;

import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Slf4j
@Component
public class AuthUtils {

    //无权限
    public static  boolean userPermissionChecker(@NotNull Long userId, @NotNull Long resourceId, @NotNull Function<Long, Long> ownerIdGetter) {
        // 获取资源所有者 ID
        Long ownerId = ownerIdGetter.apply(resourceId);

        // 权限校验
        if (!userId.equals(ownerId)) {
            log.warn(ResultCode.FAILED_UNAUTHORIZED.toString() + "id: {}", userId);
            throw new ApplicationException(Result.failed(ResultCode.FAILED_UNAUTHORIZED));
        }
        return true;
    }
    //被禁言
    public static boolean userBannedChecker(@NotNull User user){
        if(user.getState() == 1 || user.getDeleteState() == 1){
            log.warn(ResultCode.FAILED_USER_BANNED.toString()+"id: {}",user.getId());
            throw new ApplicationException(Result.failed(ResultCode.FAILED_USER_BANNED));
        }
        return true;
    }
}