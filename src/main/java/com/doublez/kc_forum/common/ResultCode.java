package com.doublez.kc_forum.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResultCode {
    SUCCESS (200,"操作成功"),
    FAILED  (1000,"操作失败"),
    FAILED_UNAUTHORIZED (1001, "未授权"),
    FAILED_PARAMS_VALIDATE (1002, "参数校验失败"),
    FAILED_FORBIDDEN (1003, "禁⽌访问"),
    FAILED_CREATE (1004, "新增失败"),
    FAILED_NOT_EXISTS (1005, "资源不存在"),
    FAILED_BOARD_NOT_EXISTS(1006,"板块不存在"),
    FAILED_TOKEN_EXISTS(1007,"TOKEN不存在"),
    INVALID_FILE_TYPE(1008,"不符合的图片格式"),
    FAIL_REFRESH_TOKEN(1009,"无效的刷新请求 (缺少凭证)"),

    FAILED_USER_EXISTS (1101, "用户已存在"),
    FAILED_USER_NOT_EXISTS (1102, "用户不存在"),
    FAILED_LOGIN (1103, "用户名或密码错误"),
    FAILED_USER_BANNED (1104, "您已被禁⾔, 请联系管理员."),
    FAILED_TWO_PWD_NOT_SAME (1105, "两次输入的密码不⼀致"),
    FAILED_MODIFY_USER(1106,"用户信息更新失败"),
    FAILED_CHECK_USERID(1107,"未获取到用户ID"),
    UPLOAD_FAILED(1108,"头像上传失败"),
    ERROR_SERVICES (2000, "服务器内部错误"),
    ERROR_TYPE_CHANGE(2001,"BeanUtil类型转化异常"),
    ERROR_IS_NULL (2001, "IS NULL."),

    FAILED_ARTICLE_NOT_EXISTS(3001,"帖子不存在"),
    FAILED_UPDATE_ARTICLE(3002,"帖子更新失败"),
    FAILED_ARTICLE_BANNED(3003,"帖子被禁言"),
    FAILED_ARTICLE_DELETE(3004,"帖子删除失败"),
    FAILED_REPLY_DELETE(3005,"回复帖子删除失败"),

    FAILED_CHANGE_LIKE(4001,"增加/删除点赞失败"),


    ERROR_SEND_EMAIL(5001, "发送邮件失败"),
    ERROR_EMAIL_ALREADY_REGISTERED(5002, "邮箱已被注册"),
    ERROR_VERIFICATION_CODE_INCORRECT(5003, "验证码错误"),
    ERROR_VERIFICATION_CODE_EXPIRED(5004, "验证码已过期"),
    ERROR_EMAIL_NOT_VERIFIED(5005, "邮箱未验证"),
    ERROR_EMAIL_NOT_FOUND(5006, "邮箱不存在"),

    ERROR_MESSAGE_NOT_VALID(6001,"消息不合法"),
    FAILED_MESSAGE_INSERT(6001,"消息创建失败"),

    FAILED_DELETE_IMAGE(7001,"删除图片失败");

    //状态码
    final int  code;
    final String message;

    @Override
    public String toString() {
        return "ResultCode{" +
                "code=" + code +
                ", message='" + message + '\'' +
                '}';
    }
}
