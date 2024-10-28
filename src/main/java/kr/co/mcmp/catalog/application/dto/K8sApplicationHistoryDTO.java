package kr.co.mcmp.catalog.application.dto;

import java.time.LocalDateTime;

import kr.co.mcmp.catalog.application.model.ApplicationStatus;
import kr.co.mcmp.catalog.application.model.K8sApplicationHistory;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class K8sApplicationHistoryDTO {
    private Integer id;
    private String namespace;
    private String clusterName;
    private Integer catalogId;
    private String catalogName;
    private ApplicationStatus status;
    private LocalDateTime installedAt;
    private LocalDateTime uninstalledAt;
    private LocalDateTime updatedAt;

    public static K8sApplicationHistoryDTO fromEntity(K8sApplicationHistory entity) {
        return K8sApplicationHistoryDTO.builder()
                .id(entity.getId())
                .namespace(entity.getNamespace())
                .clusterName(entity.getClusterName())
                .catalogId(entity.getCatalog().getId())
                .catalogName(entity.getCatalog().getTitle())
                .status(entity.getStatus())
                .installedAt(entity.getInstalledAt())
                .uninstalledAt(entity.getUninstalledAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
