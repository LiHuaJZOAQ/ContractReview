package com.contractreview.service;

public interface SystemConfigService {
    /**
     * 读取整数配置。key 不存在或 value 非整数时返回 defaultValue。
     */
    int getInt(String key, int defaultValue);

    /**
     * 读取字符串配置。key 不存在时返回 defaultValue。
     */
    String getString(String key, String defaultValue);

    /**
     * 写入配置。写入后立即清除内存缓存。
     */
    void set(String key, String value);
}
