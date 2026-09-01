package com.contractreview.service;

import com.contractreview.domain.dto.AdminUserDto;
import com.contractreview.domain.dto.OperationLogDto;
import com.contractreview.domain.dto.SystemStatsDto;
import com.contractreview.domain.dto.SystemMonitorDto;

import java.util.List;

public interface AdminService {
    SystemStatsDto getSystemStats();
    SystemMonitorDto getSystemMonitor();
    List<AdminUserDto> listUsers();
    List<OperationLogDto> listOperationLogs(int page, int size, String action);
    void updateUserRole(Long userId, String role);
    void resetUserQuota(Long userId, int quota);
    void deleteUser(Long userId);
}
