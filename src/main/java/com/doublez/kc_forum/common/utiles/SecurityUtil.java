package com.doublez.kc_forum.common.utiles;


import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.common.exception.BusinessException;
import com.doublez.kc_forum.model.User;
import jakarta.validation.constraints.NotNull;
import lombok.SneakyThrows;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class SecurityUtil {
    //生成
    @SneakyThrows
    public static String encrypt(String password){
        //判断是否为空
        if(!StringUtils.hasLength(password)){
            throw new BusinessException(ResultCode.FAILED_PARAMS_VALIDATE,"密码为空");
        }
        //生成盐值
        String salt = UUID.randomUUID().toString().replace("-", "");
        //MD5加密，uuid+password 32位16进制密文
        String s = DigestUtils.md5DigestAsHex((salt + password).getBytes(StandardCharsets.UTF_8));
        //64位16进制数据
        return salt + s;
    }
    //校对
    public static Boolean checkPassword(String inputPassword, String sqlPassword){
        if(!StringUtils.hasLength(inputPassword) || !StringUtils.hasLength(sqlPassword)){
            return false;
        }
        if(sqlPassword.length() != 64){
            return false;
        }
        String salt = sqlPassword.substring(0,32);
        //生成密文，判断对错
        String securityPassword = DigestUtils.md5DigestAsHex((salt + inputPassword).getBytes(StandardCharsets.UTF_8));

        return (salt+securityPassword).equals(sqlPassword);
    }
}
