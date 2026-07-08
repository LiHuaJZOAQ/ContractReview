package com.contractreview.controller;

import com.contractreview.aop.AuditLog;
import com.contractreview.common.R;
import com.contractreview.domain.dto.AuthRequest;
import com.contractreview.domain.dto.AuthResponse;
import com.contractreview.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @AuditLog(action = "REGISTER")
    public R<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.register(request.getUsername(), request.getPassword());
        return R.ok(response);
    }

    @PostMapping("/login")
    public R<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request.getUsername(), request.getPassword());
        return R.ok(response);
    }

    @PostMapping("/refresh")
    public R<AuthResponse> refresh(@RequestBody Map<String, String> body) {
        AuthResponse response = authService.refresh(body.get("refreshToken"));
        return R.ok(response);
    }
}
