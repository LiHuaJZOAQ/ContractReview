package com.contractreview.controller;

import com.contractreview.common.R;
import com.contractreview.domain.dto.*;
import com.contractreview.security.UserContext;
import com.contractreview.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Tag(name = "用户中心", description = "个人信息管理、密码修改、API配置")
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    @Operation(summary = "获取个人信息", description = "获取当前用户的个人信息、剩余额度和API配置")
    public R<UserProfileResponse> getProfile() {
        Long userId = UserContext.getUserId();
        return R.ok(userService.getProfile(userId));
    }

    @PutMapping("/profile")
    @Operation(summary = "修改个人信息", description = "修改用户名等基本信息")
    public R<Void> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        Long userId = UserContext.getUserId();
        userService.updateProfile(userId, request.getUsername());
        return R.ok();
    }

    @PostMapping("/password")
    @Operation(summary = "修改密码", description = "验证旧密码后设置新密码")
    public R<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        Long userId = UserContext.getUserId();
        userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return R.ok();
    }

    @PutMapping("/api-config")
    @Operation(summary = "配置自定义API", description = "设置用户自定义的LLM API地址、Key和模型")
    public R<Void> updateApiConfig(@Valid @RequestBody ApiConfigRequest request) {
        Long userId = UserContext.getUserId();
        userService.updateApiConfig(userId, request.getApiUrl(), request.getApiKey(), request.getModel());
        return R.ok();
    }
}
