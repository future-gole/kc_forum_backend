package com.doublez.kc_forum.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@Data
@TableName("email_verification")
public class EmailVerification {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String email;

    private String verificationCode;

    private LocalDateTime expiryTime;

    private Boolean verified;
}
