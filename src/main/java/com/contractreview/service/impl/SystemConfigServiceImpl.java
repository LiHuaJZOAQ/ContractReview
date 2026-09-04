package com.contractreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.contractreview.domain.entity.SystemConfig;
import com.contractreview.mapper.SystemConfigMapper;
import com.contractreview.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigMapper mapper;

    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

    @Override
    public int getInt(String key, int defaultValue) {
        String v = getString(key, null);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public String getString(String key, String defaultValue) {
        String cached = cache.get(key);
        if (cached != null) return cached;
        SystemConfig row = mapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        if (row == null) return defaultValue;
        cache.put(key, row.getConfigValue());
        return row.getConfigValue();
    }

    @Override
    public void set(String key, String value) {
        SystemConfig existing = mapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        if (existing == null) {
            SystemConfig row = new SystemConfig();
            row.setConfigKey(key);
            row.setConfigValue(value);
            mapper.insert(row);
        } else {
            existing.setConfigValue(value);
            mapper.updateById(existing);
        }
        // 写后立即清除缓存，强制下次读走 DB
        cache.remove(key);
    }
}
