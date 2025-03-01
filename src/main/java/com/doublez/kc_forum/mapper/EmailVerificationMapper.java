package com.doublez.kc_forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doublez.kc_forum.model.EmailVerification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailVerificationMapper extends BaseMapper<EmailVerification> {
}
