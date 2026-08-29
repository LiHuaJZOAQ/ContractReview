package com.contractreview.domain.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    @DisplayName("合法的用户名和密码通过校验")
    void testValidInput() {
        AuthRequest request = new AuthRequest();
        request.setUsername("admin");
        request.setPassword("password123");
        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("用户名为空时校验失败")
    void testBlankUsername() {
        AuthRequest request = new AuthRequest();
        request.setUsername("");
        request.setPassword("password123");
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    @DisplayName("用户名过短（1字符）时校验失败")
    void testUsernameTooShort() {
        AuthRequest request = new AuthRequest();
        request.setUsername("a");
        request.setPassword("password123");
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    @DisplayName("用户名过长（51字符）时校验失败")
    void testUsernameTooLong() {
        AuthRequest request = new AuthRequest();
        request.setUsername("a".repeat(51));
        request.setPassword("password123");
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    @DisplayName("密码为空时校验失败")
    void testBlankPassword() {
        AuthRequest request = new AuthRequest();
        request.setUsername("admin");
        request.setPassword("");
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    @DisplayName("密码过短（5字符）时校验失败")
    void testPasswordTooShort() {
        AuthRequest request = new AuthRequest();
        request.setUsername("admin");
        request.setPassword("12345");
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    @DisplayName("密码过长（129字符）时校验失败")
    void testPasswordTooLong() {
        AuthRequest request = new AuthRequest();
        request.setUsername("admin");
        request.setPassword("a".repeat(129));
        Set<ConstraintViolation<AuthRequest>> violations = validator.validate(request);
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    @DisplayName("边界值：2字符用户名和6字符密码通过校验")
    void testBoundaryValid() {
        AuthRequest request = new AuthRequest();
        request.setUsername("ab");
        request.setPassword("123456");
        assertTrue(validator.validate(request).isEmpty());
    }
}
