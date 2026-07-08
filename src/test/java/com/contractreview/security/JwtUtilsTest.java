package com.contractreview.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils("TestSecretKeyForJWTTesting2026VeryLongString", 7200000L, 2592000000L);
    }

    @Test
    @DisplayName("生成并验证 Access Token")
    void testGenerateAndValidateAccessToken() {
        String token = jwtUtils.generateAccessToken(1L);
        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(1L, jwtUtils.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("生成并验证 Refresh Token")
    void testGenerateAndValidateRefreshToken() {
        String token = jwtUtils.generateRefreshToken(2L);
        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));
        assertEquals(2L, jwtUtils.getUserIdFromToken(token));
    }

    @Test
    @DisplayName("篡改的 Token 验证失败")
    void testTamperedToken() {
        String token = jwtUtils.generateAccessToken(1L);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtUtils.validateToken(tampered));
    }

    @Test
    @DisplayName("空字符串 Token 抛出异常")
    void testEmptyToken() {
        assertThrows(Exception.class, () -> jwtUtils.validateToken(""));
    }

    @Test
    @DisplayName("不同密钥生成的 Token 不能互相验证")
    void testDifferentSecret() {
        JwtUtils other = new JwtUtils("DifferentSecretKeyForTestingPurposesOnly123", 7200000L, 2592000000L);
        String token = jwtUtils.generateAccessToken(1L);
        assertFalse(other.validateToken(token));
    }
}
