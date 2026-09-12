package com.contractreview.service;

import com.contractreview.domain.dto.UserProfileResponse;

public interface UserService {
    UserProfileResponse getProfile(Long userId);
    void updateProfile(Long userId, String username);
    void changePassword(Long userId, String oldPassword, String newPassword);
    void updateApiConfig(Long userId, String apiUrl, String apiKey, String model);
}
