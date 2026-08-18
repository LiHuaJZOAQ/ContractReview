package com.contractreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contractreview.common.BusinessException;
import com.contractreview.domain.dto.AdminUserDto;
import com.contractreview.domain.dto.SystemStatsDto;
import com.contractreview.domain.entity.Law;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.entity.User;
import com.contractreview.mapper.LawMapper;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.mapper.UserMapper;
import com.contractreview.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final ReviewTaskMapper taskMapper;
    private final LawMapper lawMapper;

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
}
