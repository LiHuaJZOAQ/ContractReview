package com.contractreview.config;

import com.contractreview.common.R;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JsonErrorPagesConfig implements ErrorController {

    private static final String ERROR_PATH = "/error";

    @RequestMapping(ERROR_PATH)
    public ResponseEntity<R<Void>> handleError(HttpServletRequest request) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusObj instanceof Integer ? (Integer) statusObj : 500;
        Object msgObj = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        String message = msgObj != null ? msgObj.toString() : defaultMessage(status);
        R<Void> r = R.error(status, message);
        return ResponseEntity.status(status).body(r);
    }

    private String defaultMessage(int status) {
        return switch (status) {
            case 400 -> "请求参数错误";
            case 401 -> "未登录或Token缺失";
            case 403 -> "权限不足";
            case 404 -> "资源不存在";
            case 405 -> "方法不允许";
            case 500 -> "服务器内部错误";
            default -> "请求失败";
        };
    }
}
