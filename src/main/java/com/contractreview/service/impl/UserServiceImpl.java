package com.contractreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contractreview.common.BusinessException;
import com.contractreview.domain.dto.UserProfileResponse;
import com.contractreview.domain.entity.User;
import com.contractreview.domain.entity.UserApiConfig;
import com.contractreview.mapper.UserApiConfigMapper;
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
    private final UserApiConfigMapper userApiConfigMapper;

    @Override
    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        UserApiConfig activeConfig = userApiConfigMapper.selectOne(
                new LambdaQueryWrapper<UserApiConfig>()
                        .eq(UserApiConfig::getUserId, userId)
                        .eq(UserApiConfig::getIsActive, 1)
                        .last("LIMIT 1"));
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getReviewQuota(),
                user.getRole(),
                activeConfig != null ? activeConfig.getApiUrl() : null,
                activeConfig != null,
                activeConfig != null ? activeConfig.getModel() : null,
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
        UserApiConfig existingConfig = userApiConfigMapper.selectOne(
                new LambdaQueryWrapper<UserApiConfig>()
                        .eq(UserApiConfig::getUserId, userId)
                        .eq(UserApiConfig::getIsActive, 1)
                        .last("LIMIT 1"));
        if (existingConfig != null) {
            if (apiUrl != null) existingConfig.setApiUrl(apiUrl);
            if (apiKey != null) existingConfig.setApiKey(apiKey);
            if (model != null) existingConfig.setModel(model);
            userApiConfigMapper.updateById(existingConfig);
        } else {
            UserApiConfig newConfig = new UserApiConfig();
            newConfig.setUserId(userId);
            newConfig.setConfigName("默认配置");
            newConfig.setApiUrl(apiUrl != null ? apiUrl : "");
            newConfig.setApiKey(apiKey != null ? apiKey : "");
            newConfig.setModel(model != null ? model : "");
            newConfig.setIsActive(1);
            userApiConfigMapper.insert(newConfig);
        }
    }
}
