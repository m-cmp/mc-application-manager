package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Kubernetes Pod 로그 수집 서비스
 * Pod의 로그를 수집하고 필터링하는 기능을 제공합니다.
 */
@Component
@Slf4j
public class KubernetesLogCollector {

    /**
     * Pod의 에러 로그만 수집합니다.
     */
    public List<String> collectErrorLogs(KubernetesClient client, String namespace, String podName) {
        return collectLogs(client, namespace, podName, LogLevel.ERROR);
    }

    /**
     * Pod의 모든 로그를 수집합니다.
     */
    public List<String> collectAllLogs(KubernetesClient client, String namespace, String podName) {
        return collectLogs(client, namespace, podName, LogLevel.ALL);
    }

    /**
     * Pod의 특정 레벨 로그를 수집합니다.
     */
    public List<String> collectLogs(KubernetesClient client, String namespace, String podName, LogLevel level) {
        List<String> logs = new ArrayList<>();
        
        try {
            log.debug("Pod 로그 수집 시작 - Namespace: {}, Pod: {}, Level: {}", namespace, podName, level);
            
            // Pod 존재 여부 및 상태 확인
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                log.warn("Pod를 찾을 수 없습니다: {}", podName);
                return logs;
            }
            
            // Pod가 초기화 중이거나 실행 중이 아닌 경우 로그 수집 건너뛰기
            String podPhase = pod.getStatus().getPhase();
            if (!"Running".equals(podPhase)) {
                log.debug("Pod가 실행 중이 아니므로 로그 수집을 건너뜁니다 - Pod: {}, Phase: {}", podName, podPhase);
                return logs;
            }

            // Pod의 모든 컨테이너에서 로그 수집
            collectLogsFromAllContainers(client, namespace, podName, pod, logs, level);

            log.debug("Pod 로그 수집 완료 - 총 {} 줄", logs.size());
            
        } catch (Exception e) {
            log.error("Pod 로그 수집 중 오류 발생 - Pod: {}, Error: {}", podName, e.getMessage(), e);
        }
        
        return logs;
    }

    /**
     * 앱 이름으로 Pod들을 찾아서 로그를 수집합니다.
     */
    public List<String> collectAppLogs(KubernetesClient client, String namespace, String appName, LogLevel level) {
        List<String> allLogs = new ArrayList<>();
        
        try {
            log.debug("앱 로그 수집 시작 - Namespace: {}, App: {}, Level: {}", namespace, appName, level);
            
            // 앱과 관련된 Pod들 찾기
            List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .list()
                    .getItems()
                    .stream()
                    .filter(pod -> {
                        String podName = pod.getMetadata().getName();
                        return podName.startsWith(appName) || 
                               podName.startsWith(appName.toLowerCase()) ||
                               podName.contains(appName) ||
                               podName.contains(appName.toLowerCase());
                    })
                    .toList();

            log.debug("앱 '{}'과 관련된 Pod {} 개 발견", appName, pods.size());

            // 각 Pod의 로그 수집
            for (Pod pod : pods) {
                String podName = pod.getMetadata().getName();
                collectLogsFromAllContainers(client, namespace, podName, pod, allLogs, level);
            }

            log.debug("앱 로그 수집 완료 - 총 {} 줄", allLogs.size());
            
        } catch (Exception e) {
            log.error("앱 로그 수집 중 오류 발생 - App: {}, Error: {}", appName, e.getMessage(), e);
        }
        
        return allLogs;
    }

    /**
     * Pod의 모든 컨테이너에서 로그를 수집합니다.
     */
    private void collectLogsFromAllContainers(KubernetesClient client, String namespace, String podName, 
                                            Pod pod, List<String> logs, LogLevel level) {
        try {
            // Pod에 컨테이너가 있는지 확인
            if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
                log.debug("Pod에 컨테이너 상태 정보가 없습니다 - Pod: {}", podName);
                return;
            }
            
            List<String> containerNames = pod.getStatus().getContainerStatuses().stream()
                    .map(containerStatus -> containerStatus.getName())
                    .collect(Collectors.toList());
            
            log.debug("Pod {}의 컨테이너들: {}", podName, containerNames);
            
            // 각 컨테이너에서 로그 수집
            for (String containerName : containerNames) {
                try {
                    String logContent = client.pods()
                            .inNamespace(namespace)
                            .withName(podName)
                            .inContainer(containerName) // 특정 컨테이너 지정
                            .tailingLines(50) // 컨테이너당 최근 50줄
                            .getLog();
                    
                    if (logContent != null && !logContent.isEmpty()) {
                        String[] lines = logContent.split("\n");
                        for (String line : lines) {
                            if (line.trim().isEmpty()) continue;
                            
                            if (shouldIncludeLog(line, level)) {
                                String trimmedLine = line.trim();
                                // 로그 길이를 2000자로 제한
                                if (trimmedLine.length() > 2000) {
                                    trimmedLine = trimmedLine.substring(0, 2000) + "... [truncated]";
                                }
                                // 컨테이너 이름을 접두사로 추가
                                String prefixedLine = String.format("[%s:%s] %s", podName, containerName, trimmedLine);
                                logs.add(prefixedLine);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("컨테이너 {}에서 로그 수집 실패 - Pod: {}, Error: {}", 
                            containerName, podName, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Pod {}의 컨테이너 로그 수집 중 오류 발생: {}", podName, e.getMessage(), e);
        }
    }

    /**
     * 로그 레벨에 따라 로그를 필터링합니다.
     */
    private boolean shouldIncludeLog(String logLine, LogLevel level) {
        if (level == LogLevel.ALL) {
            return true;
        }
        
        String lowerLogLine = logLine.toLowerCase();
        
        // 1. 구조화된 로그 형식 분석 (JSON, key=value 등)
        if (isStructuredLog(lowerLogLine)) {
            return analyzeStructuredLog(lowerLogLine, level);
        }
        
        // 2. 일반적인 로그 키워드 기반 분류
        return analyzeKeywordLog(lowerLogLine, level);
    }
    
    /**
     * 구조화된 로그인지 확인합니다.
     */
    private boolean isStructuredLog(String logLine) {
        return logLine.contains("level=") || 
               logLine.contains("\"level\":") ||
               logLine.contains("severity=") ||
               logLine.contains("\"severity\":") ||
               logLine.contains("log_level=") ||
               logLine.contains("\"log_level\":") ||
               logLine.contains("timestamp=") ||
               logLine.contains("\"timestamp\":");
    }
    
    /**
     * 구조화된 로그를 분석합니다.
     */
    private boolean analyzeStructuredLog(String logLine, LogLevel level) {
        // level= 형식
        if (logLine.contains("level=")) {
            switch (level) {
                case ERROR:
                    return logLine.matches(".*level=(error|fatal|panic|critical).*");
                case WARN:
                    return logLine.matches(".*level=(warn|warning).*") || 
                           analyzeStructuredLog(logLine, LogLevel.ERROR);
                case INFO:
                    return logLine.matches(".*level=(info|information).*") ||
                           analyzeStructuredLog(logLine, LogLevel.WARN);
                case DEBUG:
                    return logLine.matches(".*level=(debug|trace|verbose).*") ||
                           analyzeStructuredLog(logLine, LogLevel.INFO);
                default:
                    return false;
            }
        }
        
        // JSON 형식
        if (logLine.contains("\"level\":")) {
            switch (level) {
                case ERROR:
                    return logLine.matches(".*\"level\"\\s*:\\s*\"(error|fatal|panic|critical)\".*");
                case WARN:
                    return logLine.matches(".*\"level\"\\s*:\\s*\"(warn|warning)\".*") ||
                           analyzeStructuredLog(logLine, LogLevel.ERROR);
                case INFO:
                    return logLine.matches(".*\"level\"\\s*:\\s*\"(info|information)\".*") ||
                           analyzeStructuredLog(logLine, LogLevel.WARN);
                case DEBUG:
                    return logLine.matches(".*\"level\"\\s*:\\s*\"(debug|trace|verbose)\".*") ||
                           analyzeStructuredLog(logLine, LogLevel.INFO);
                default:
                    return false;
            }
        }
        
        // severity= 형식
        if (logLine.contains("severity=")) {
            switch (level) {
                case ERROR:
                    return logLine.matches(".*severity=(error|fatal|panic|critical).*");
                case WARN:
                    return logLine.matches(".*severity=(warn|warning).*") ||
                           analyzeStructuredLog(logLine, LogLevel.ERROR);
                case INFO:
                    return logLine.matches(".*severity=(info|information).*") ||
                           analyzeStructuredLog(logLine, LogLevel.WARN);
                case DEBUG:
                    return logLine.matches(".*severity=(debug|trace|verbose).*") ||
                           analyzeStructuredLog(logLine, LogLevel.INFO);
                default:
                    return false;
            }
        }
        
        return false;
    }
    
    /**
     * 키워드 기반 로그를 분석합니다.
     */
    private boolean analyzeKeywordLog(String logLine, LogLevel level) {
        switch (level) {
            case ERROR:
                return logLine.matches(".*\\b(error|exception|failed|failure|fatal|panic|critical|alert|emergency)\\b.*") ||
                       logLine.matches(".*\\[ERROR\\].*") ||
                       logLine.matches(".*ERROR:.*") ||
                       logLine.matches(".*Exception.*") ||
                       logLine.matches(".*Failed.*") ||
                       logLine.matches(".*FATAL.*");
            case WARN:
                return logLine.matches(".*\\b(warn|warning|caution|notice)\\b.*") ||
                       logLine.matches(".*\\[WARN\\].*") ||
                       logLine.matches(".*WARNING:.*") ||
                       logLine.matches(".*WARN:.*") ||
                       analyzeKeywordLog(logLine, LogLevel.ERROR);
            case INFO:
                return logLine.matches(".*\\b(info|information|notice)\\b.*") ||
                       logLine.matches(".*\\[INFO\\].*") ||
                       logLine.matches(".*INFO:.*") ||
                       logLine.matches(".*Starting.*") ||
                       logLine.matches(".*Started.*") ||
                       logLine.matches(".*Stopping.*") ||
                       logLine.matches(".*Stopped.*") ||
                       analyzeKeywordLog(logLine, LogLevel.WARN);
            case DEBUG:
                return logLine.matches(".*\\b(debug|trace|verbose|detail)\\b.*") ||
                       logLine.matches(".*\\[DEBUG\\].*") ||
                       logLine.matches(".*DEBUG:.*") ||
                       logLine.matches(".*\\[TRACE\\].*") ||
                       logLine.matches(".*TRACE:.*") ||
                       analyzeKeywordLog(logLine, LogLevel.INFO);
            default:
                return true;
        }
    }

    /**
     * 로그 레벨 열거형
     */
    public enum LogLevel {
        ALL,    // 모든 로그
        DEBUG,  // DEBUG 이상
        INFO,   // INFO 이상
        WARN,   // WARN 이상
        ERROR   // ERROR만
    }
}
