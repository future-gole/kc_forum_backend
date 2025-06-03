package com.doublez.kc_forum.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum ResultCode {
    SUCCESS (200,"操作成功", HttpStatus.OK),
    FAILED  (1000,"操作失败",HttpStatus.BAD_REQUEST),
    FAILED_UNAUTHORIZED (1001, "未授权",HttpStatus.UNAUTHORIZED),
    FAILED_PARAMS_VALIDATE (1002, "参数校验失败",HttpStatus.BAD_REQUEST),
    FAILED_FORBIDDEN (1003, "禁⽌访问",HttpStatus.FORBIDDEN),
    FAILED_CREATE (1004, "新增失败",HttpStatus.BAD_REQUEST),
    FAILED_NOT_EXISTS (1005, "资源不存在",HttpStatus.NOT_FOUND),
    FAILED_BOARD_NOT_EXISTS(1006,"板块不存在",HttpStatus.NOT_FOUND),
    FAILED_TOKEN_EXISTS(1007,"请求凭证不存在",HttpStatus.UNAUTHORIZED),
    INVALID_FILE_TYPE(1008,"不符合的文件格式",HttpStatus.BAD_REQUEST),
    FAIL_REFRESH_TOKEN(1009,"无效的刷新请求 (缺少凭证)",HttpStatus.UNAUTHORIZED),

    FAILED_USER_EXISTS (1101, "用户已存在",HttpStatus.CONFLICT), // HTTP 409 尝试创建一个已经存在的资源
    FAILED_USER_NOT_EXISTS (1102, "用户不存在",HttpStatus.BAD_REQUEST),
    FAILED_LOGIN (1103, "用户名或密码错误",HttpStatus.BAD_REQUEST),
    FAILED_USER_BANNED (1104, "您已被禁⾔, 请联系管理员.",HttpStatus.UNAUTHORIZED),
    FAILED_TWO_PWD_NOT_SAME (1105, "两次输入的密码不⼀致",HttpStatus.BAD_REQUEST),
    FAILED_MODIFY_USER(1106,"用户信息更新失败", HttpStatus.INTERNAL_SERVER_ERROR),
    FAILED_CHECK_USERID(1107,"未获取到用户ID", HttpStatus.BAD_REQUEST),
    UPLOAD_AVATAR_FAILED(1108,"头像上传失败", HttpStatus.INTERNAL_SERVER_ERROR),
    UPLOAD_IMAGE_FAILED(1109,"图片上传失败",HttpStatus.INTERNAL_SERVER_ERROR),

    ERROR_SERVICES (2000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    ERROR_TYPE_CHANGE(2001,"BeanUtil类型转化异常", HttpStatus.INTERNAL_SERVER_ERROR),
    ERROR_IS_NULL (2002, "空指针异常", HttpStatus.INTERNAL_SERVER_ERROR),

    FAILED_ARTICLE_NOT_EXISTS(3001,"帖子不存在", HttpStatus.BAD_REQUEST),
    FAILED_UPDATE_ARTICLE(3002,"帖子更新失败",HttpStatus.BAD_REQUEST),
    FAILED_ARTICLE_BANNED(3003,"帖子被禁言",HttpStatus.FORBIDDEN),
    FAILED_ARTICLE_DELETE(3004,"帖子删除失败",HttpStatus.INTERNAL_SERVER_ERROR),
    FAILED_REPLY_DELETE(3005,"回复帖子删除失败",HttpStatus.INTERNAL_SERVER_ERROR),

    FAILED_CHANGE_LIKE(4001,"增加/删除点赞失败",HttpStatus.INTERNAL_SERVER_ERROR),


    ERROR_SEND_EMAIL(5001, "发送邮件失败", HttpStatus.INTERNAL_SERVER_ERROR),
    ERROR_EMAIL_ALREADY_REGISTERED(5002, "邮箱已被注册",HttpStatus.CONFLICT),
    ERROR_VERIFICATION_CODE_INCORRECT(5003, "验证码错误", HttpStatus.BAD_REQUEST),
    ERROR_VERIFICATION_CODE_EXPIRED(5004, "验证码已过期", HttpStatus.BAD_REQUEST),
    ERROR_EMAIL_NOT_VERIFIED(5005, "邮箱未验证", HttpStatus.BAD_REQUEST),
    ERROR_EMAIL_NOT_FOUND(5006, "邮箱不存在", HttpStatus.NOT_FOUND),

    ERROR_MESSAGE_NOT_VALID(6001,"消息不合法", HttpStatus.BAD_REQUEST),
    FAILED_MESSAGE_INSERT(6001,"消息创建失败", HttpStatus.INTERNAL_SERVER_ERROR),

    FAILED_DELETE_IMAGE(7001,"删除图片失败", HttpStatus.INTERNAL_SERVER_ERROR),;

    //状态码
    private final int  code;
    private final String message;
    private final HttpStatus httpStatus;

    @Override
    public String toString() {
        return "ResultCode{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", httpStatus=" + httpStatus.value() +
                '}';
    }
}
