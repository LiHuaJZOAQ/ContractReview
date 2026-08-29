package com.contractreview.controller;

import com.contractreview.common.R;
import com.contractreview.domain.dto.AdminUserDto;
import com.contractreview.domain.dto.SystemStatsDto;
import com.contractreview.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "管理后台", description = "用户管理、系统监控")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    @Operation(summary = "系统统计", description = "获取系统整体统计数据")
    public R<SystemStatsDto> getSystemStats() {
        return R.ok(adminService.getSystemStats());
    }

    @GetMapping("/users")
    @Operation(summary = "用户列表", description = "获取所有用户信息")
    public R<List<AdminUserDto>> listUsers() {
        return R.ok(adminService.listUsers());
    }

    @PutMapping("/users/{userId}/role")
    @Operation(summary = "修改用户角色")
    public R<Void> updateUserRole(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        adminService.updateUserRole(userId, body.get("role"));
        return R.ok();
    }

    @PutMapping("/users/{userId}/quota")
    @Operation(summary = "重置用户额度")
    public R<Void> resetUserQuota(@PathVariable Long userId, @RequestBody Map<String, Integer> body) {
        adminService.resetUserQuota(userId, body.getOrDefault("quota", 10));
        return R.ok();
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "删除用户")
    public R<Void> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return R.ok();
    }
}
