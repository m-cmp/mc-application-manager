package kr.co.mcmp.softwarecatalog.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** The current form values, not just the saved catalog's Ingress defaults. */
@Data
public class K8sIngressCheckRequest {
    @NotBlank private String namespace; // CB-Tumblebug project, not the workload namespace
    @NotBlank private String clusterName;
    @NotNull @Positive private Long catalogId;
    private Boolean ingressEnabled;
    private String ingressHost;
    private String ingressPath;
    private String ingressClass;
    private Boolean ingressTlsEnabled;
    private String ingressTlsSecret;

    public DeploymentRequest toDeploymentRequest() {
        return DeploymentRequest.builder().namespace(namespace).clusterName(clusterName).catalogId(catalogId)
                .ingressEnabled(ingressEnabled).ingressHost(ingressHost).ingressPath(ingressPath)
                .ingressClass(ingressClass).ingressTlsEnabled(ingressTlsEnabled).ingressTlsSecret(ingressTlsSecret).build();
    }
}
