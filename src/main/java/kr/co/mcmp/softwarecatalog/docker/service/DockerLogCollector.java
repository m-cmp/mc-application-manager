package kr.co.mcmp.softwarecatalog.docker.service;

import java.util.List;

/**
 * 도커 로그 수집 서비스 인터페이스
 */
public interface DockerLogCollector {
    
    /**
     * VM ID와 컨테이너 이름으로 도커 로그 수집
     * 
     * @param vmId VM ID
     * @param containerName 컨테이너 이름
     * @return 수집된 로그 목록
     */
    List<String> collectLogs(String vmId, String containerName);
    
    /**
     * VM ID로 모든 컨테이너의 로그 수집
     * 
     * @param vmId VM ID
     * @return 수집된 로그 목록 (컨테이너별로 구분)
     */
    List<String> collectAllContainerLogs(String vmId);
    
    /**
     * 특정 컨테이너의 최근 N개 로그 수집
     * 
     * @param vmId VM ID
     * @param containerName 컨테이너 이름
     * @param maxLines 최대 로그 라인 수
     * @return 수집된 로그 목록
     */
    List<String> collectLogsWithLimit(String vmId, String containerName, int maxLines);
    
    /**
     * 특정 시간 이후의 로그 수집
     * 
     * @param vmId VM ID
     * @param containerName 컨테이너 이름
     * @param sinceTime 시작 시간 (ISO 8601 형식)
     * @return 수집된 로그 목록
     */
    List<String> collectLogsSince(String vmId, String containerName, String sinceTime);
    
    /**
     * Docker 로그를 수집하고 UnifiedLog에 저장
     * 
     * @param deploymentId 배포 ID
     * @param vmId VM ID
     * @param containerName 컨테이너 이름
     */
    void collectAndSaveLogs(Long deploymentId, String vmId, String containerName);
}
