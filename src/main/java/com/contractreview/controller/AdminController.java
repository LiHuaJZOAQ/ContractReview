package com.contractreview.controller;

import com.contractreview.common.R;
import com.contractreview.domain.dto.AdminUserDto;
import com.contractreview.domain.dto.OperationLogDto;
import com.contractreview.domain.dto.SystemMonitorDto;
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

    @GetMapping("/monitor")
    @Operation(summary = "系统监控", description = "获取JVM内存线程CPU等运行时指标")
    public R<SystemMonitorDto> getSystemMonitor() {
        return R.ok(adminService.getSystemMonitor());
    }

    @GetMapping("/operations")
    @Operation(summary = "操作日志", description = "分页查询用户操作日志")
    public R<List<OperationLogDto>> listOperations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action) {
        return R.ok(adminService.listOperationLogs(page, size, action));
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
    @Operation(summary = "设置用户积分", description = "将指定用户的积分直接设为 quota 值（不再有总额/剩余概念）")
    public R<Void> resetUserQuota(@PathVariable Long userId, @RequestBody Map<String, Integer> body) {
        Integer quota = body.get("quota");
        if (quota == null) {
            throw new com.contractreview.common.BusinessException(400, "必须传入 quota 字段");
        }
        adminService.resetUserQuota(userId, quota);
        return R.ok();
    }

    @GetMapping("/quota-default")
    @Operation(summary = "查询新用户默认积分", description = "管理员面板展示的当前默认积分")
    public R<Map<String, Integer>> getDefaultQuota() {
        return R.ok(Map.of("quota", adminService.getDefaultQuota()));
    }

    @PutMapping("/quota-default")
    @Operation(summary = "设置新用户默认积分", description = "调整注册时给新用户的默认积分，仅影响新注册用户")
    public R<Void> setDefaultQuota(@RequestBody Map<String, Integer> body) {
        Integer quota = body.get("quota");
        if (quota == null) {
            throw new com.contractreview.common.BusinessException(400, "必须传入 quota 字段");
        }
        adminService.setDefaultQuota(quota);
        return R.ok();
    }

    @DeleteMapping("/users/{userId}")
    @Operation(summary = "删除用户")
    public R<Void> deleteUser(@PathVariable Long userId) {
        adminService.deleteUser(userId);
        return R.ok();
    }
}
