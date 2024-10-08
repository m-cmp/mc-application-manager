package kr.co.mcmp.ape.cbtumblebug.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class K8sClusterDto {
    private String id;
    private String name;
    private String connectionName;
    private String status;
    private String version;
    private AccessInfo accessInfo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccessInfo {
        private String endpoint;
        private String kubeconfig;
    }
}