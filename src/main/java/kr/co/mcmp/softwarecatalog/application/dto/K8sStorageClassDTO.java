package kr.co.mcmp.softwarecatalog.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class K8sStorageClassDTO {
    private String name;
    private String provisioner;
    private Boolean defaultClass;
    private String reclaimPolicy;
    private String volumeBindingMode;
}
