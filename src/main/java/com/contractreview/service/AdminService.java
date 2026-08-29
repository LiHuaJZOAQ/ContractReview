package com.contractreview.service;

import com.contractreview.domain.dto.AdminUserDto;
import com.contractreview.domain.dto.SystemStatsDto;

import java.util.List;

public interface AdminService {
    SystemStatsDto getSystemStats();
    List<AdminUserDto> listUsers();
    void updateUserRole(Long userId, String role);
    void resetUserQuota(Long userId, int quota);
    void deleteUser(Long userId);
}
