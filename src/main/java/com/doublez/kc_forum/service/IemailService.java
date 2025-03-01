package com.doublez.kc_forum.service;

import com.doublez.kc_forum.common.Result;

public interface IemailService {

    /**
     * 发送邮箱
     * @param email
     * @return
     */
    Result sendVerificationCode(String email);

    /**
     * 验证邮箱
     */
    Result verifyEmail(String email, String code);
//    /**
//     * 发送html邮件
//     * @param toEmail
//     * @param verificationCode
//     */
//    void sendHtmlVerificationEmail(String toEmail, String verificationCode);

//    /**
//     * 发送正常邮件
//     * @param toEmail
//     * @param verificationCode
//     */
//    Result sendVerificationEmail(String toEmail, String verificationCode);

    void clearExpiredVerificationCodes();
}
