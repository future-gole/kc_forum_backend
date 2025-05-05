package com.doublez.kc_forum.controller;

import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.service.impl.EmailServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RequestMapping("/email")
@RestController
@Slf4j
public class emailController {
    @Autowired
    private EmailServiceImpl emailService;
    @PostMapping("/sendVerificationCode")
    @Operation(summary = "发送邮箱验证码")
    public Result sendVerificationCode(@RequestParam @Email String email) {
        log.info("发送验证码到邮箱: {}", email);
        emailService.sendVerificationCode(email);
        return Result.sucess();
    }

    @PostMapping("/verifyEmail")
    @Operation(summary = "验证邮箱")
    public Result verifyEmail(@RequestParam @Email String email,
                              @RequestParam String code) {
        log.info("验证邮箱: {}", email);
         emailService.verifyEmail(email, code);
         return Result.sucess();
    }
}
