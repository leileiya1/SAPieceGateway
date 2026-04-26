package com.sapiece.nova.sapiecegateway.health;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统健康检查指示器
 * 提供系统整体运行状态、JVM内存、熔断器状态等信息
 *
 * @author SAPiece
 * @since 2025-11-08
 */
@Slf4j
@Component
public class SystemHealthIndicator implements ReactiveHealthIndicator {

    @Override
    public Mono<Health> health() {
        log.debug("执行系统健康检查...");

        try {
            Map<String, Object> details = new HashMap<>();

            // 1. JVM内存信息
            details.put("memory", getMemoryInfo());

            // 2. 系统运行时间
            details.put("uptime", getUptime());

            // 3. 熔断器状态
            details.put("circuitBreakers", getCircuitBreakerStatus());

            // 4. 线程信息
            details.put("threads", getThreadInfo());

            log.debug("系统健康检查完成");

            return Mono.just(Health.up()
                    .withDetail("status", "UP")
                    .withDetail("application", "SAPiece Gateway")
                    .withDetails(details)
                    .build());

        } catch (Exception e) {
            log.error("系统健康检查异常", e);
            return Mono.just(Health.down()
                    .withDetail("status", "DOWN")
                    .withDetail("error", e.getClass().getName())
                    .withDetail("message", e.getMessage())
                    .build());
        }
    }

    /**
     * 获取JVM内存信息
     *
     * @return 内存使用情况
     */
    private Map<String, Object> getMemoryInfo() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();

        Map<String, Object> memoryInfo = new HashMap<>();

        // 堆内存
        Map<String, String> heap = new HashMap<>();
        heap.put("init", formatBytes(heapMemoryUsage.getInit()));
        heap.put("used", formatBytes(heapMemoryUsage.getUsed()));
        heap.put("committed", formatBytes(heapMemoryUsage.getCommitted()));
        heap.put("max", formatBytes(heapMemoryUsage.getMax()));
        heap.put("usage", String.format("%.2f%%", (double) heapMemoryUsage.getUsed() / heapMemoryUsage.getMax() * 100));
        memoryInfo.put("heap", heap);

        // 非堆内存
        Map<String, String> nonHeap = new HashMap<>();
        nonHeap.put("init", formatBytes(nonHeapMemoryUsage.getInit()));
        nonHeap.put("used", formatBytes(nonHeapMemoryUsage.getUsed()));
        nonHeap.put("committed", formatBytes(nonHeapMemoryUsage.getCommitted()));
        nonHeap.put("max", formatBytes(nonHeapMemoryUsage.getMax()));
        memoryInfo.put("nonHeap", nonHeap);

        return memoryInfo;
    }

    /**
     * 格式化字节数为可读格式
     *
     * @param bytes 字节数
     * @return 格式化后的字符串（如：256MB）
     */
    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "N/A";
        }
        if (bytes < 1024) {
            return bytes + "B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f%sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * 获取系统运行时间
     *
     * @return 运行时间字符串
     */
    private String getUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long days = uptimeMs / (24 * 60 * 60 * 1000);
        long hours = (uptimeMs % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (uptimeMs % (60 * 60 * 1000)) / (60 * 1000);
        long seconds = (uptimeMs % (60 * 1000)) / 1000;

        return String.format("%d天 %d小时 %d分钟 %d秒", days, hours, minutes, seconds);
    }

    /**
     * 获取 Sentinel 熔断规则状态
     */
    private Map<String, Object> getCircuitBreakerStatus() {
        Map<String, Object> status = new HashMap<>();
        var rules = DegradeRuleManager.getRules();
        status.put("engine", "Sentinel");
        status.put("ruleCount", rules != null ? rules.size() : 0);
        status.put("rules", rules != null
                ? rules.stream().map(r -> Map.of(
                        "resource", r.getResource(),
                        "grade", r.getGrade(),
                        "count", r.getCount(),
                        "timeWindow", r.getTimeWindow()))
                  .toList()
                : java.util.List.of());
        return status;
    }

    /**
     * 获取线程信息
     *
     * @return 线程统计信息
     */
    private Map<String, Object> getThreadInfo() {
        Map<String, Object> threadInfo = new HashMap<>();
        threadInfo.put("count", Thread.activeCount());
        threadInfo.put("peakCount", ManagementFactory.getThreadMXBean().getPeakThreadCount());
        threadInfo.put("totalStarted", ManagementFactory.getThreadMXBean().getTotalStartedThreadCount());
        threadInfo.put("daemon", ManagementFactory.getThreadMXBean().getDaemonThreadCount());

        return threadInfo;
    }
}
