package com.contractreview.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SystemMonitorDto {
    private int availableProcessors;
    private long jvmMemoryMax;
    private long jvmMemoryUsed;
    private long jvmMemoryCommitted;
    private int threadCount;
    private int peakThreadCount;
    private long uptime;
    private String javaVersion;
    private String osName;
    private double cpuLoad;
    private long heapUsed;
    private long heapMax;
    private long nonHeapUsed;
}
