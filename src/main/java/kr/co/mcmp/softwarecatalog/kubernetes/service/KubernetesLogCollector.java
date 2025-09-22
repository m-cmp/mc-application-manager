package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import kr.co.mcmp.softwarecatalog.application.dto.UnifiedLogDTO;
import kr.co.mcmp.softwarecatalog.application.service.UnifiedLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kubernetes Pod 로그 수집 서비스
 * Pod의 로그를 수집하고 필터링하는 기능을 제공합니다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KubernetesLogCollector {
    
    private final UnifiedLogService unifiedLogService;

    /**
     * Pod의 에러 로그만 수집합니다.
     */
    public List<String> collectErrorLogs(KubernetesClient client, String namespace, String podName) {
        return collectLogs(client, namespace, podName, LogLevel.ERROR);
    }
    
    /**
     * Pod의 로그를 수집하고 UnifiedLog에 저장합니다.
     */
    public void collectAndSaveLogs(KubernetesClient client, Long deploymentId, String namespace, String podName, String containerName, String clusterName) {
        try {
            log.info("Starting Kubernetes log collection and save for deployment: {}, namespace: {}, pod: {}", 
                    deploymentId, namespace, podName);
            
            // Pod 존재 여부 및 상태 확인
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                log.warn("Pod를 찾을 수 없습니다: {}", podName);
                return;
            }
            
            // Pod가 초기화 중이거나 실행 중이 아닌 경우 로그 수집 건너뛰기
            String podPhase = pod.getStatus().getPhase();
            if (!"Running".equals(podPhase)) {
                log.debug("Pod가 실행 중이 아니므로 로그 수집을 건너뜁니다 - Pod: {}, Phase: {}", podName, podPhase);
                return;
            }
            
            // ERROR 로그 수집 및 저장
            collectAndSaveErrorLogs(client, deploymentId, namespace, podName, containerName, clusterName);
            
            // Pod 상태 로그 수집 및 저장
            collectAndSavePodLogs(client, deploymentId, namespace, podName, containerName, clusterName, pod);
            
            log.info("Completed Kubernetes log collection and save for deployment: {}", deploymentId);
            
        } catch (Exception e) {
            log.error("Kubernetes log collection and save failed for deployment: {}, pod: {}", deploymentId, podName, e);
        }
    }

    /**
     * Pod의 모든 로그를 수집합니다.
     */
    public List<String> collectAllLogs(KubernetesClient client, String namespace, String podName) {
        return collectLogs(client, namespace, podName, LogLevel.ALL);
    }

    /**
     * Pod의 특정 레벨 로그를 수집합니다.
     * 현재는 ERROR와 Pod 로그만 수집합니다.
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

            // ERROR 로그만 수집 (DEBUG, INFO는 비활성화)
            if (level == LogLevel.ERROR) {
                collectLogsFromAllContainers(client, namespace, podName, pod, logs, level);
            }
            // DEBUG, INFO 로그 수집은 주석처리
            // else if (level == LogLevel.DEBUG || level == LogLevel.INFO) {
            //     collectLogsFromAllContainers(client, namespace, podName, pod, logs, level);
            // }

            log.debug("Pod 로그 수집 완료 - 총 {} 줄", logs.size());
            
        } catch (Exception e) {
            log.error("Pod 로그 수집 중 오류 발생 - Pod: {}, Error: {}", podName, e.getMessage(), e);
        }
        
        return logs;
    }

    /**
     * 앱 이름으로 Pod들을 찾아서 로그를 수집합니다.
     * 현재는 ERROR와 Pod 로그만 수집합니다.
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

            // ERROR 로그만 수집 (DEBUG, INFO는 비활성화)
            if (level == LogLevel.ERROR) {
                for (Pod pod : pods) {
                    String podName = pod.getMetadata().getName();
                    collectLogsFromAllContainers(client, namespace, podName, pod, allLogs, level);
                }
            }
            // DEBUG, INFO 로그 수집은 주석처리
            // else if (level == LogLevel.DEBUG || level == LogLevel.INFO) {
            //     for (Pod pod : pods) {
            //         String podName = pod.getMetadata().getName();
            //         collectLogsFromAllContainers(client, namespace, podName, pod, allLogs, level);
            //     }
            // }

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
     * 현재는 ERROR 로그만 필터링합니다.
     */
    private boolean shouldIncludeLog(String logLine, LogLevel level) {
        if (level == LogLevel.ALL) {
            return true;
        }
        
        // ERROR 로그만 필터링 (DEBUG, INFO는 비활성화)
        if (level == LogLevel.ERROR) {
            String lowerLogLine = logLine.toLowerCase();
            
            // 1. 구조화된 로그 형식 분석 (JSON, key=value 등)
            if (isStructuredLog(lowerLogLine)) {
                return analyzeStructuredLog(lowerLogLine, level);
            }
            
            // 2. 일반적인 로그 키워드 기반 분류
            return analyzeKeywordLog(lowerLogLine, level);
        }
        
        // DEBUG, INFO 로그 필터링은 주석처리
        // String lowerLogLine = logLine.toLowerCase();
        // if (isStructuredLog(lowerLogLine)) {
        //     return analyzeStructuredLog(lowerLogLine, level);
        // }
        // return analyzeKeywordLog(lowerLogLine, level);
        
        return false;
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
     * 현재는 ERROR 로그만 분석합니다.
     */
    private boolean analyzeStructuredLog(String logLine, LogLevel level) {
        // level= 형식
        if (logLine.contains("level=")) {
            switch (level) {
                case ERROR:
                    return logLine.matches(".*level=(error|fatal|panic|critical).*");
                // DEBUG, INFO, WARN 로그 분석은 주석처리
                // case WARN:
                //     return logLine.matches(".*level=(warn|warning).*") || 
                //            analyzeStructuredLog(logLine, LogLevel.ERROR);
                // case INFO:
                //     return logLine.matches(".*level=(info|information).*") ||
                //            analyzeStructuredLog(logLine, LogLevel.WARN);
                // case DEBUG:
                //     return logLine.matches(".*level=(debug|trace|verbose).*") ||
                //            analyzeStructuredLog(logLine, LogLevel.INFO);
                default:
                    return false;
            }
        }
        
        // JSON 형식
        if (logLine.contains("\"level\":")) {
            switch (level) {
                case ERROR:
                    return logLine.matches(".*\"level\"\\s*:\\s*\"(error|fatal|panic|critical)\".*");
                // DEBUG, INFO, WARN 로그 분석은 주석처리
                // case WARN:
                //     return logLine.matches(".*\"level\"\\s*:\\s*\"(warn|warning)\".*") ||
                //            analyzeStructuredLog(logLine, LogLevel.ERROR);
                // case INFO:
                //     return logLine.matches(".*\"level\"\\s*:\\s*\"(info|information)\".*") ||
                //            analyzeStructuredLog(logLine, LogLevel.WARN);
                // case DEBUG:
                //     return logLine.matches(".*\"level\"\\s*:\\s*\"(debug|trace|verbose)\".*") ||
                //            analyzeStructuredLog(logLine, LogLevel.INFO);
                default:
                    return false;
            }
        }
        
        // severity= 형식
        if (logLine.contains("severity=")) {
            switch (level) {
                case ERROR:
                    return logLine.matches(".*severity=(error|fatal|panic|critical).*");
                // DEBUG, INFO, WARN 로그 분석은 주석처리
                // case WARN:
                //     return logLine.matches(".*severity=(warn|warning).*") ||
                //            analyzeStructuredLog(logLine, LogLevel.ERROR);
                // case INFO:
                //     return logLine.matches(".*severity=(info|information).*") ||
                //            analyzeStructuredLog(logLine, LogLevel.WARN);
                // case DEBUG:
                //     return logLine.matches(".*severity=(debug|trace|verbose).*") ||
                //            analyzeStructuredLog(logLine, LogLevel.INFO);
                default:
                    return false;
            }
        }
        
        return false;
    }
    
    /**
     * 키워드 기반 로그를 분석합니다.
     * 현재는 ERROR 로그만 분석합니다.
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
            // DEBUG, INFO, WARN 로그 분석은 주석처리
            // case WARN:
            //     return logLine.matches(".*\\b(warn|warning|caution|notice)\\b.*") ||
            //            logLine.matches(".*\\[WARN\\].*") ||
            //            logLine.matches(".*WARNING:.*") ||
            //            logLine.matches(".*WARN:.*") ||
            //            analyzeKeywordLog(logLine, LogLevel.ERROR);
            // case INFO:
            //     return logLine.matches(".*\\b(info|information|notice)\\b.*") ||
            //            logLine.matches(".*\\[INFO\\].*") ||
            //            logLine.matches(".*INFO:.*") ||
            //            logLine.matches(".*Starting.*") ||
            //            logLine.matches(".*Started.*") ||
            //            logLine.matches(".*Stopping.*") ||
            //            logLine.matches(".*Stopped.*") ||
            //            analyzeKeywordLog(logLine, LogLevel.WARN);
            // case DEBUG:
            //     return logLine.matches(".*\\b(debug|trace|verbose|detail)\\b.*") ||
            //            logLine.matches(".*\\[DEBUG\\].*") ||
            //            logLine.matches(".*DEBUG:.*") ||
            //            logLine.matches(".*\\[TRACE\\].*") ||
            //            logLine.matches(".*TRACE:.*") ||
            //            analyzeKeywordLog(logLine, LogLevel.INFO);
            default:
                return false;
        }
    }
    
    /**
     * ERROR 로그를 수집하고 UnifiedLog에 저장합니다.
     */
    private void collectAndSaveErrorLogs(KubernetesClient client, Long deploymentId, String namespace, String podName, String containerName, String clusterName) {
        try {
            List<String> errorLogs = collectLogs(client, namespace, podName, LogLevel.ERROR);
            
            for (String logMessage : errorLogs) {
                UnifiedLogDTO logDTO = UnifiedLogDTO.builder()
                        .deploymentId(deploymentId)
                        .loggedAt(LocalDateTime.now())
                        .severity(UnifiedLogDTO.LogSeverity.ERROR.getValue())
                        .module(UnifiedLogDTO.LogSourceType.KUBERNETES.getValue())
                        .logMessage(logMessage)
                        .namespace(namespace)
                        .podName(podName)
                        .containerName(containerName)
                        .clusterName(clusterName)
                        .errorCode("K8S_ERROR")
                        .build();
                
                unifiedLogService.saveLog(logDTO);
            }
            
            log.debug("Saved {} error logs for deployment: {}, pod: {}", errorLogs.size(), deploymentId, podName);
            
        } catch (Exception e) {
            log.error("Failed to collect and save error logs for deployment: {}, pod: {}", deploymentId, podName, e);
        }
    }
    
    /**
     * Pod 상태 로그를 수집하고 UnifiedLog에 저장합니다.
     */
    private void collectAndSavePodLogs(KubernetesClient client, Long deploymentId, String namespace, String podName, String containerName, String clusterName, Pod pod) {
        try {
            List<String> podLogs = new ArrayList<>();
            
            // Pod 상태 정보 수집
            String podPhase = pod.getStatus().getPhase();
            String podStatus = String.format("Pod Status: %s", podPhase);
            podLogs.add(podStatus);
            
            // Pod가 실행 중인 경우 추가 정보 수집
            if ("Running".equals(podPhase)) {
                podLogs.add("Pod is running normally");
                
                // 컨테이너 상태 정보 수집
                if (pod.getStatus().getContainerStatuses() != null) {
                    for (var containerStatus : pod.getStatus().getContainerStatuses()) {
                        String statusContainerName = containerStatus.getName();
                        boolean ready = containerStatus.getReady();
                        String state = containerStatus.getState().toString();
                        String containerStatusMsg = String.format("Container %s: %s (Ready: %s)", statusContainerName, state, ready);
                        podLogs.add(containerStatusMsg);
                        
                        // 마지막 전환 시간
                        if (containerStatus.getLastState() != null && containerStatus.getLastState().getTerminated() != null) {
                            String lastTransitionTime = containerStatus.getLastState().getTerminated().getFinishedAt();
                            if (lastTransitionTime != null) {
                                podLogs.add(String.format("Last transition time: %s", lastTransitionTime));
                            }
                        }
                    }
                }
            }
            
            // Pod 로그를 UnifiedLog에 저장
            for (String logMessage : podLogs) {
                UnifiedLogDTO logDTO = UnifiedLogDTO.builder()
                        .deploymentId(deploymentId)
                        .loggedAt(LocalDateTime.now())
                        .severity(UnifiedLogDTO.LogSeverity.INFO.getValue())
                        .module(UnifiedLogDTO.LogSourceType.KUBERNETES.getValue())
                        .logMessage(logMessage)
                        .namespace(namespace)
                        .podName(podName)
                        .containerName(containerName)
                        .clusterName(clusterName)
                        .build();
                
                unifiedLogService.saveLog(logDTO);
            }
            
            log.debug("Saved {} pod logs for deployment: {}, pod: {}", podLogs.size(), deploymentId, podName);
            
        } catch (Exception e) {
            log.error("Failed to collect and save pod logs for deployment: {}, pod: {}", deploymentId, podName, e);
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
