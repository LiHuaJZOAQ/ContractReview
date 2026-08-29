package com.contractreview.controller;

import com.contractreview.aop.AuditLog;
import com.contractreview.common.R;
import com.contractreview.domain.dto.AuthRequest;
import com.contractreview.domain.dto.AuthResponse;
import com.contractreview.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "认证", description = "用户注册、登录、刷新令牌")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @AuditLog(action = "REGISTER")
    @Operation(summary = "用户注册", description = "创建新用户账号",
            responses = {
                    @ApiResponse(responseCode = "200", description = "注册成功"),
                    @ApiResponse(responseCode = "400", description = "用户名已存在或参数错误")
            })
    public R<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.register(request.getUsername(), request.getPassword());
        return R.ok(response);
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "验证用户名密码，返回 JWT 令牌",
            responses = {
                    @ApiResponse(responseCode = "200", description = "登录成功"),
                    @ApiResponse(responseCode = "401", description = "用户名或密码错误")
            })
    public R<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request.getUsername(), request.getPassword());
        return R.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用 refreshToken 获取新的 accessToken",
            responses = {
                    @ApiResponse(responseCode = "200", description = "刷新成功"),
                    @ApiResponse(responseCode = "401", description = "refreshToken 无效或已过期")
            })
    public R<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        AuthResponse response = authService.refresh(body.get("refreshToken"));
        return R.ok(response);
    }
}
