package com.doublez.kc_forum.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.doublez.kc_forum.common.Result;
import com.doublez.kc_forum.common.ResultCode;
import com.doublez.kc_forum.common.exception.ApplicationException;
import com.doublez.kc_forum.mapper.EmailVerificationMapper;
import com.doublez.kc_forum.mapper.UserMapper;
import com.doublez.kc_forum.model.EmailVerification;
import com.doublez.kc_forum.model.User;
import com.doublez.kc_forum.service.IemailService;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Random;

@Service
@Slf4j
public class EmailServiceImpl implements IemailService {
    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmailVerificationMapper verificationMapper;

    //发送邮箱私有类，添加code
    private Result sendVerificationEmail(String toEmail, String verificationCode) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("注册验证码");
            message.setText("您好，您的注册验证码是: " + verificationCode +
                    "，有效期10分钟，请勿泄露给他人。");
            mailSender.send(message);
            log.info("验证邮件已发送至: {}", toEmail);
        } catch (Exception e) {
            log.error("发送邮件失败: {}", e.getMessage());
            throw new ApplicationException(Result.failed(ResultCode.ERROR_SEND_EMAIL));
        }
        return Result.sucess();
    }

    @Scheduled(cron = "0 0 * * * ?")
    @Override
    public void clearExpiredVerificationCodes() {
        log.info("开始清理过期的验证码...");
        //获取当前时间
        LocalDateTime now = LocalDateTime.now();
        verificationMapper.delete(new LambdaQueryWrapper<EmailVerification>()
                .le(EmailVerification::getExpiryTime,now));
        log.info("清理成功");
    }

    // 如果需要发送HTML格式邮件，可以使用这个方法
    //发送邮箱私有类，添加code
    private Result sendHtmlVerificationEmail(String toEmail, String verificationCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("注册验证码");

            String htmlContent = "<!DOCTYPE html>"
                    + "<html>"
                    + "<head>"
                    + "<meta charset='UTF-8'>"
                    + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                    + "<style>"
                    + "  @import url('https://fonts.googleapis.com/css2?family=Poppins:wght@400;600&display=swap');"
                    + "  .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; }"
                    + "  .header { background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%); padding: 32px; text-align: center; }"
                    + "  .logo { width: 120px; }"
                    + "  .content { padding: 40px 32px; color: #4b5563; font-family: 'Poppins', sans-serif; }"
                    + "  .code-box { background: #f3f4f6; padding: 24px; border-radius: 8px; text-align: center; margin: 24px 0; }"
                    + "  .verification-code { font-size: 32px; letter-spacing: 4px; color: #1f2937; font-weight: 600; margin: 16px 0; }"
                    + "  .note { color: #6b7280; font-size: 14px; line-height: 1.5; }"
                    + "  .button { background: #6366f1; color: white !important; padding: 12px 24px; border-radius: 6px; text-decoration: none; display: inline-block; margin-top: 24px; }"
                    + "  .footer { background: #f9fafb; padding: 24px; text-align: center; font-size: 12px; color: #6b7280; }"
                    + "  @media (max-width: 600px) {"
                    + "    .container { margin: 16px; }"
                    + "    .verification-code { font-size: 24px; }"
                    + "  }"
                    + "</style>"
                    + "</head>"
                    + "<body>"
                    + "<div class='container'>"
                    + "  <div class='content'>"
                    + "    <h1 style='color: #1f2937; margin: 0 0 16px 0;'>欢迎加入小科论坛！</h1>"
                    + "    <p style='margin: 0 0 24px 0;'>请使用以下验证码完成注册：</p>"
                    + "    <div class='code-box'>"
                    + "      <div style='display: flex; justify-content: center; margin-bottom: 16px;'>"
                    + "        <svg xmlns='http://www.w3.org/2000/svg' width='48' height='48' viewBox='0 0 24 24' fill='#6366f1'>"
                    + "          <path d='M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 14H4V8l8 5 8-5v10zm-8-7L4 6h16l-8 5z'/>"
                    + "        </svg>"
                    + "      </div>"
                    + "      <div class='verification-code'>" + verificationCode + "</div>"
                    + "      <p class='note'>此验证码将在10分钟后失效</p>"
                    + "    </div>"
                    + "    <p class='note'>如果这不是您本人的操作，请忽略此邮件。</p>"
                    + "  </div>"
                    + "  <div class='footer'>"
                    + "    <p>© 2025 KC-Company. All rights reserved.</p>"
                    + "    <p>需要帮助？联系 support@KC-company.com</p>"
                    + "  </div>"
                    + "</div>"
                    + "</body>"
                    + "</html>";
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("HTML验证邮件已发送至: {}", toEmail);
        } catch (Exception e) {
            log.error("发送HTML邮件失败: {}", e.getMessage());
            throw new ApplicationException(Result.failed(ResultCode.ERROR_SEND_EMAIL));
        }
        return Result.sucess();
    }

    @Transactional
    @Override
    public Result sendVerificationCode(String email) {
//        //检查邮箱是否被注册
//        if(!existEmail(email)){
//            return Result.failed(ResultCode.ERROR_EMAIL_ALREADY_REGISTERED);
//        }
        //生成6位随机验证码
        String verificationCode = String.format("%06d", new Random().nextInt(999999));

        // 查找是否已有验证记录
        LambdaQueryWrapper<EmailVerification> verificationQuery = new LambdaQueryWrapper<>();
        verificationQuery.eq(EmailVerification::getEmail, email);
        EmailVerification verification = verificationMapper.selectOne(verificationQuery);

        if (verification == null) {
            verification = new EmailVerification();
            verification.setEmail(email);
        }

        // 更新验证码信息
        verification.setVerificationCode(verificationCode);
        verification.setExpiryTime(LocalDateTime.now().plusMinutes(10));
        verification.setVerified(false);

        // 保存或更新验证码
        if (verification.getId() == null) {
            verificationMapper.insert(verification);
        } else {
            verificationMapper.updateById(verification);
        }

        // 发送验证邮件
        return sendHtmlVerificationEmail(email, verificationCode);
    }

    @Override
    public Result verifyEmail(String email, String code) {
        // 查找验证记录
        EmailVerification verification = verificationMapper.selectOne(new LambdaQueryWrapper<EmailVerification>()
                .eq(EmailVerification::getEmail, email));

        if (verification == null) {
            return Result.failed(ResultCode.ERROR_EMAIL_NOT_FOUND);
        }

        // 检查验证码是否正确
        if (!verification.getVerificationCode().equals(code)) {
            return Result.failed(ResultCode.ERROR_VERIFICATION_CODE_INCORRECT);
        }

        // 检查验证码是否过期
        if (verification.getExpiryTime().isBefore(LocalDateTime.now())) {
            return Result.failed(ResultCode.ERROR_VERIFICATION_CODE_EXPIRED);
        }

        // 标记为已验证
        verification.setVerified(true);
        verificationMapper.updateById(verification);

        return Result.sucess();
    }

    private boolean existEmail(String email) {
        //检查邮箱是否被注册
        User existingUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email).eq(User::getDeleteState,0));
        return existingUser != null;
    }
}