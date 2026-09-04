package com.contractreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contractreview.common.BusinessException;
import com.contractreview.domain.dto.AdminUserDto;
import com.contractreview.domain.dto.OperationLogDto;
import com.contractreview.domain.dto.SystemMonitorDto;
import com.contractreview.domain.dto.SystemStatsDto;
import com.contractreview.domain.entity.Law;
import com.contractreview.domain.entity.OperationLog;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.entity.User;
import com.contractreview.mapper.LawMapper;
import com.contractreview.mapper.OperationLogMapper;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.mapper.UserMapper;
import com.contractreview.service.AdminService;
import com.contractreview.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final ReviewTaskMapper taskMapper;
    private final LawMapper lawMapper;
    private final OperationLogMapper operationLogMapper;
    private final SystemConfigService systemConfigService;

    @Override
    public SystemStatsDto getSystemStats() {
        long totalUsers = userMapper.selectCount(null);
        long totalTasks = taskMapper.selectCount(null);
        long successTasks = taskMapper.selectCount(
                new LambdaQueryWrapper<ReviewTask>().eq(ReviewTask::getStatus, "SUCCESS"));
        long failedTasks = taskMapper.selectCount(
                new LambdaQueryWrapper<ReviewTask>().eq(ReviewTask::getStatus, "FAILED"));
        long processingTasks = totalTasks - successTasks - failedTasks;
        long totalLaws = lawMapper.selectCount(null);

        return new SystemStatsDto(totalUsers, totalTasks, successTasks, failedTasks,
                Math.max(0, processingTasks), totalLaws);
    }

    @Override
    public List<AdminUserDto> listUsers() {
        return userMapper.selectList(null).stream()
                .map(u -> new AdminUserDto(
                        u.getId(),
                        u.getUsername(),
                        u.getRole(),
                        u.getReviewQuota(),
                        u.getCreatedAt() != null ? u.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null
                ))
                .collect(Collectors.toList());
    }

    @Override
    public void updateUserRole(Long userId, String role) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new BusinessException(400, "无效的角色类型");
        }
        user.setRole(role);
        userMapper.updateById(user);
    }

    @Override
    public void resetUserQuota(Long userId, int quota) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setReviewQuota(quota);
        userMapper.updateById(user);
    }

    @Override
    public void deleteUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        userMapper.deleteById(userId);
    }

    @Override
    public int getDefaultQuota() {
        return systemConfigService.getInt("default_quota", 100);
    }

    @Override
    public void setDefaultQuota(int quota) {
        if (quota < 0 || quota > 100000) {
            throw new BusinessException(400, "默认值范围必须为 0 ~ 100000");
        }
        systemConfigService.set("default_quota", String.valueOf(quota));
    }

    @Override
    public SystemMonitorDto getSystemMonitor() {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();

        double cpuLoad = osBean.getSystemLoadAverage();
        if (cpuLoad < 0) cpuLoad = 0;

        return new SystemMonitorDto(
                runtime.availableProcessors(),
                heap.getMax() > 0 ? heap.getMax() : heap.getCommitted(),
                heap.getUsed(),
                heap.getCommitted(),
                threadBean.getThreadCount(),
                threadBean.getPeakThreadCount(),
                runtimeBean.getUptime(),
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                Math.round(cpuLoad * 100.0) / 100.0,
                heap.getUsed(),
                heap.getMax() > 0 ? heap.getMax() : heap.getCommitted(),
                nonHeap.getUsed()
        );
    }

    @Override
    public List<OperationLogDto> listOperationLogs(int page, int size, String action) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (action != null && !action.isEmpty()) {
            wrapper.eq(OperationLog::getAction, action);
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);
        wrapper.last("LIMIT " + size + " OFFSET " + (page - 1) * size);

        List<OperationLog> logs = operationLogMapper.selectList(wrapper);
        List<Long> userIds = logs.stream()
                .map(OperationLog::getUserId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> usernameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        return logs.stream()
                .map(log -> new OperationLogDto(
                        log.getId(),
                        log.getUserId(),
                        log.getUserId() != null ? usernameMap.getOrDefault(log.getUserId(), "未知") : "系统",
                        log.getAction(),
                        log.getTaskId(),
                        log.getDetail(),
                        log.getIpAddress(),
                        log.getCreatedAt() != null ? log.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null
                ))
                .collect(Collectors.toList());
    }
}
