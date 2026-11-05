package org.example.deepusecode.character.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一业务错误码。
 * code 同时用于 ApiResponse.code 与 HTTP 状态码，避免两套语义打架。
 */
@Getter
public enum ErrorCode {

    OK(0, "操作成功", HttpStatus.OK),

    BAD_REQUEST(400, "请求参数不正确", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "没有权限执行该操作", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "资源不存在", HttpStatus.NOT_FOUND),
    CONFLICT(409, "资源状态冲突", HttpStatus.CONFLICT),
    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS),

    AI_SERVICE_ERROR(502, "AI 服务暂时不可用", HttpStatus.BAD_GATEWAY),

    INTERNAL_ERROR(500, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(int code, String defaultMessage, HttpStatus status) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }
}
