package com.contractreview.service;

import com.contractreview.domain.dto.AuthResponse;

public interface AuthService {
    AuthResponse register(String username, String password);
    AuthResponse login(String username, String password);
    AuthResponse refresh(String refreshToken);
}
