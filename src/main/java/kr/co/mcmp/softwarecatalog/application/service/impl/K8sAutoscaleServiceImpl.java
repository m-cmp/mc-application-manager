package kr.co.mcmp.softwarecatalog.application.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sNodeGroupAutoscaleRequest;
import kr.co.mcmp.softwarecatalog.application.service.K8sAutoscaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class K8sAutoscaleServiceImpl implements K8sAutoscaleService {
    
    private final CbtumblebugRestApi cbtumblebugRestApi;
    
    @Override
    public boolean changeNodeGroupAutoscaleSize(String nsId, String k8sClusterId, String k8sNodeGroupName, 
                                              K8sNodeGroupAutoscaleRequest request) {
        try {
            log.info("Changing K8S node group autoscale size: nsId={}, clusterId={}, nodeGroup={}, desiredSize={}", 
                    nsId, k8sClusterId, k8sNodeGroupName, request.getDesiredNodeSize());
            
            // Tumblebug API 호출
            String result = cbtumblebugRestApi.changeK8sNodeGroupAutoscaleSize(nsId, k8sClusterId, k8sNodeGroupName, request);
            
            log.info("Successfully changed K8S node group autoscale size: {}", result);
            return true;
            
        } catch (RestClientException e) {
            String errorMsg = e.getMessage();
            // Manual 모드 에러는 무시 (노드가 이미 생성되어 있을 가능성)
            if (errorMsg != null && (errorMsg.contains("autoScaleMode") || errorMsg.contains("minNodeSize") || errorMsg.contains("maxNodeSize"))) {
                log.warn("AutoScale API failed (manual mode), but treating as success to continue: {}", errorMsg);
                return true;
            }
            log.error("Failed to change K8S node group autoscale size: nsId={}, clusterId={}, nodeGroup={}", 
                    nsId, k8sClusterId, k8sNodeGroupName, e);
            return false;
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            // Manual 모드 에러는 무시 (노드가 이미 생성되어 있을 가능성)
            if (errorMsg != null && (errorMsg.contains("autoScaleMode") || errorMsg.contains("minNodeSize") || errorMsg.contains("maxNodeSize"))) {
                log.warn("AutoScale API failed (manual mode), but treating as success to continue: {}", errorMsg);
                return true;
            }
            log.error("Unexpected error while changing K8S node group autoscale size", e);
            return false;
        }
    }
    
    @Override
    public boolean scaleOutNodeGroup(String namespace, String clusterName, String nodeGroupName, 
                                    int currentSize, int maxSize) {
        if (currentSize >= maxSize) {
            log.warn("Cannot scale out: current size {} >= max size {}", currentSize, maxSize);
            return false;
        }
        
        int newSize = Math.min(currentSize + 1, maxSize);
        
        // Manual 모드 대응: 
        // - minNodeSize = 현재 노드 수
        // - desiredNodeSize = 현재 + 1
        // - maxNodeSize = desired + 2
        int minNodeSize = currentSize;
        int newMaxNodeSize = newSize + 2;
        
        K8sNodeGroupAutoscaleRequest request = new K8sNodeGroupAutoscaleRequest(
            String.valueOf(newSize),
            String.valueOf(newMaxNodeSize), // maxNodeSize = desired + 2
            String.valueOf(minNodeSize)    // minNodeSize = current
        );
        
        log.info("Scaling out K8S node group: {} -> {} (min={}, max={})", currentSize, newSize, minNodeSize, newMaxNodeSize);
        return changeNodeGroupAutoscaleSize(namespace, clusterName, nodeGroupName, request);
    }
    
    
}
