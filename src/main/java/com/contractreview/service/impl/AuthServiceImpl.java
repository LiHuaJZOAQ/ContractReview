package com.contractreview.service.impl;

import com.contractreview.common.BusinessException;
import com.contractreview.domain.dto.AuthResponse;
import com.contractreview.domain.entity.User;
import com.contractreview.domain.enums.ErrorCode;
import com.contractreview.mapper.UserMapper;
import com.contractreview.security.JwtUtils;
import com.contractreview.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional
    public AuthResponse register(String username, String password) {
        if (userMapper.selectCount(null) > 0) {
            User existing = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getUsername, username));
            if (existing != null) {
                throw new BusinessException(400, "用户名已存在");
            }
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(org.springframework.security.crypto.bcrypt.BCrypt.hashpw(password,
                org.springframework.security.crypto.bcrypt.BCrypt.gensalt()));
        user.setReviewQuota(10);
        user.setVersion(0);
        userMapper.insert(user);

        String token = jwtUtils.generateAccessToken(user.getId());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        redisTemplate.opsForValue().set("refresh:token:" + refreshToken, user.getId().toString(), 30, TimeUnit.DAYS);

        return new AuthResponse(user.getId(), token, refreshToken);
    }

    @Override
    public AuthResponse login(String username, String password) {
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username));
        if (user == null || !org.springframework.security.crypto.bcrypt.BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = jwtUtils.generateAccessToken(user.getId());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        redisTemplate.opsForValue().set("refresh:token:" + refreshToken, user.getId().toString(), 30, TimeUnit.DAYS);

        return new AuthResponse(user.getId(), token, refreshToken);
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        String key = "refresh:token:" + refreshToken;
        String userIdStr = (String) redisTemplate.opsForValue().get(key);
        if (userIdStr == null) {
            throw new BusinessException(401, "Refresh Token 无效或已过期");
        }
        redisTemplate.delete(key);

        Long userId = Long.valueOf(userIdStr);
        String newToken = jwtUtils.generateAccessToken(userId);
        String newRefreshToken = jwtUtils.generateRefreshToken(userId);
        redisTemplate.opsForValue().set("refresh:token:" + newRefreshToken, userId.toString(), 30, TimeUnit.DAYS);

        return new AuthResponse(userId, newToken, newRefreshToken);
    }
}
