package com.contractreview.service.impl;

import com.contractreview.common.BusinessException;
import com.contractreview.domain.dto.AuthResponse;
import com.contractreview.domain.entity.User;
import com.contractreview.mapper.UserMapper;
import com.contractreview.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        authService = new AuthServiceImpl(userMapper, jwtUtils, redisTemplate);
    }

    @Test
    @DisplayName("注册成功")
    void testRegisterSuccess() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        when(jwtUtils.generateAccessToken(1L)).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(1L)).thenReturn("refresh-token");

        AuthResponse response = authService.register("testuser", "password123");

        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals("access-token", response.getToken());
        assertEquals("refresh-token", response.getRefreshToken());
    }

    @Test
    @DisplayName("注册重复用户名抛异常")
    void testRegisterDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        User existing = new User();
        existing.setUsername("testuser");
        when(userMapper.selectOne(any())).thenReturn(existing);

        assertThrows(BusinessException.class, () -> authService.register("testuser", "password123"));
    }

    @Test
    @DisplayName("登录成功")
    void testLoginSuccess() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPasswordHash(BCrypt.hashpw("password123", BCrypt.gensalt()));

        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtUtils.generateAccessToken(1L)).thenReturn("access-token");
        when(jwtUtils.generateRefreshToken(1L)).thenReturn("refresh-token");

        AuthResponse response = authService.login("testuser", "password123");

        assertNotNull(response);
        assertEquals(1L, response.getUserId());
    }

    @Test
    @DisplayName("登录密码错误抛异常")
    void testLoginWrongPassword() {
        User user = new User();
        user.setPasswordHash(BCrypt.hashpw("correct", BCrypt.gensalt()));
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThrows(BusinessException.class, () -> authService.login("testuser", "wrong"));
    }

    @Test
    @DisplayName("登录不存在的用户抛异常")
    void testLoginUserNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> authService.login("nonexistent", "password123"));
    }

    @Test
    @DisplayName("Refresh Token 有效则返回新 Token")
    void testRefreshTokenSuccess() {
        when(valueOps.get("refresh:token:old-refresh")).thenReturn("1");
        when(jwtUtils.generateAccessToken(1L)).thenReturn("new-access");
        when(jwtUtils.generateRefreshToken(1L)).thenReturn("new-refresh");

        AuthResponse response = authService.refresh("old-refresh");

        assertNotNull(response);
        assertEquals("new-access", response.getToken());
        assertEquals("new-refresh", response.getRefreshToken());
        verify(redisTemplate).delete("refresh:token:old-refresh");
    }

    @Test
    @DisplayName("Refresh Token 无效抛异常")
    void testRefreshTokenInvalid() {
        when(valueOps.get("refresh:token:invalid")).thenReturn(null);

        assertThrows(BusinessException.class, () -> authService.refresh("invalid"));
    }
}
