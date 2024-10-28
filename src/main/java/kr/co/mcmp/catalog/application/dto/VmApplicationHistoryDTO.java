package kr.co.mcmp.catalog.application.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import kr.co.mcmp.catalog.application.model.ApplicationStatus;
import kr.co.mcmp.catalog.application.model.VmApplicationHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VmApplicationHistoryDTO {

    private Integer id;
    private String namespace;
    private String mciName;
    private String vmName;
    private Integer catalogId;
    private String catalogName;
    private ApplicationStatus status;
    private LocalDateTime installedAt;
    private LocalDateTime uninstalledAt;
    private LocalDateTime updatedAt;

    public static VmApplicationHistoryDTO fromEntity(VmApplicationHistory entity) {
        return VmApplicationHistoryDTO.builder()
                .id(entity.getId())
                .namespace(entity.getNamespace())
                .mciName(entity.getMciName())
                .vmName(entity.getVmName())
                .catalogId(entity.getCatalog().getId())
                .catalogName(entity.getCatalog().getTitle())
                .status(entity.getStatus())
                .installedAt(entity.getInstalledAt())
                .uninstalledAt(entity.getUninstalledAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
