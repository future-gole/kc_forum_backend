package com.doublez.kc_forum.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Result<T> {
    @JsonInclude(JsonInclude.Include.ALWAYS)//总是参与序列化
    private int code;
    @JsonInclude(JsonInclude.Include.ALWAYS)//总是参与序列化
    private String message;
    @JsonInclude(JsonInclude.Include.ALWAYS)//总是参与序列化
    private T data;

    // 新增字段：用于存放刷新的 Access Token
    @JsonInclude(JsonInclude.Include.NON_NULL) // 仅在不为 null 时参与序列化
    private String newAccessToken;

    public Result(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public Result(String newAccessToken) {
        this.newAccessToken = newAccessToken;
    }
    // 新增一个包含所有字段（除了 newAccessToken）的构造函数，方便工厂方法使用
    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    /**
     * 成功的返回
     */
    public static <T> Result<T> sucess(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> sucess(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> sucess(String message) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message);
    }

    public static <T> Result<T> sucess() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage());
    }

    /**
     * 失败的返回
     */
    public static <T> Result<T> failed(String message) {
        return new Result<>(ResultCode.FAILED.getCode(), message);
    }

    public static <T> Result<T> failed(T data) {
        return new Result<>(ResultCode.FAILED.getCode(), ResultCode.FAILED.getMessage(), data);
    }

    public static <T> Result<T> failed(String message, T data) {
        return new Result<>(ResultCode.FAILED.getCode(), message, data);
    }

    public static <T> Result<T> failed(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage());
    }

}
