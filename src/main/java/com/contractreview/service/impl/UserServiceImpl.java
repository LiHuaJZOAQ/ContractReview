package com.contractreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contractreview.common.BusinessException;
import com.contractreview.domain.dto.UserProfileResponse;
import com.contractreview.domain.entity.User;
import com.contractreview.mapper.UserMapper;
import com.contractreview.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private static final int DEFAULT_QUOTA = 10;

    @Override
    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getReviewQuota(),
                DEFAULT_QUOTA,
                user.getRole(),
                user.getCustomApiUrl(),
                user.getCustomApiKey() != null && !user.getCustomApiKey().isEmpty(),
                user.getCustomModel(),
                user.getCreatedAt() != null ? user.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null
        );
    }

    @Override
    public void updateProfile(Long userId, String username) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (username != null && !username.equals(user.getUsername())) {
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, username));
            if (count > 0) {
                throw new BusinessException(400, "用户名已存在");
            }
            user.setUsername(username);
        }
        userMapper.updateById(user);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!BCrypt.checkpw(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(400, "旧密码不正确");
        }
        user.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        userMapper.updateById(user);
    }

    @Override
    public void updateApiConfig(Long userId, String apiUrl, String apiKey, String model) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (apiUrl != null) user.setCustomApiUrl(apiUrl);
        if (apiKey != null) user.setCustomApiKey(apiKey);
        if (model != null) user.setCustomModel(model);
        userMapper.updateById(user);
    }
}
