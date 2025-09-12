package kr.co.mcmp.ape.dto.reqDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
    @Schema(description = "Jenkins job DTO")
public abstract class JenkinsJobDto {

    @Schema(description = "Namespace", example = "ns01")
    private String namespace;
    

    @JsonIgnore
    @Schema(hidden = true)
    public abstract String getJobName();
    public abstract Map<String, List<String>> convertToSpecificParams();

    public Map<String, List<String>> convertToJenkinsParams() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("NAMESPACE", List.of(this.namespace));
        params.putAll(convertToSpecificParams());
        return params;
    }

    @Getter
    @Setter
    @Schema(description = "VM application installation job")
    public static class VmApplicationInstall extends JenkinsJobDto {
        @Schema(description = "List of applications to install", example = "nginx")
        private List<String> applications;

        @Schema(description = "VM to install on", example = "vm01")
        private String vmName;

        @Schema(description = "MCIS name", example = "mci01")
        private String mciName;

        private String version;

        @Override
        @JsonIgnore
        @Schema(hidden = true)
        public String getJobName() {
            return "vm_application_install";
        }

        @Override
        public Map<String, List<String>> convertToSpecificParams() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("APPLICATIONS", List.of(String.join(",", this.applications)));
            // params.put("APPLICATIONS", List.of(this.applications));
            params.put("MCI_ID", List.of(this.mciName));
            params.put("VM_ID", List.of(this.vmName));
            return params;
        }
    }
    @Getter
    @Setter
    @Schema(description = "VM application uninstallation job")
    public static class VmApplicationUninstall extends JenkinsJobDto {
        @Schema(description = "List of applications to uninstall", example = "nginx")
        private List<String> applications;

        @Schema(description = "MCIS name", example = "mci01")
        private String mciName;

        @Schema(description = "VM to install on", example = "vm01")
        private String vmName;

        private String version;

        @Override
        @JsonIgnore
        @Schema(hidden = true)
        public String getJobName() {
            return "vm_application_uninstall";
        }

        @Override
        public Map<String, List<String>> convertToSpecificParams() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("APPLICATIONS", List.of(String.join(",", this.applications)));
            // params.put("APPLICATIONS", List.of(this.applications));
            params.put("MCI_ID", List.of(this.mciName));
            params.put("VM_ID", List.of(this.vmName));
            return params;
        }
    }

    @Getter
    @Setter
    @Schema(description = "Helm chart installation job")
    public static class HelmChartInstall extends JenkinsJobDto {
        @Schema(description = "K8s cluster name", example = "cluster01", required = true)
        private String clusterName;

        @Schema(description = "List of Helm charts to install", example = "[\"nginx\"]")
        private List<String> helmCharts;
        // @Schema(description = "설치할 Helm 차트", example = "nginx")
        // private String helmCharts;
        private String version;

        
        @Schema(description = "HPA (Horizontal Pod AutoScaler) activation", example = "false")
        private boolean enableHpa;

        @Schema(description = "HPA minimum replica count", example = "1", minimum = "1")
        private int hpaMinReplicas;

        @Schema(description = "HPA maximum replica count", example = "10", minimum = "1")
        private int hpaMaxReplicas;

        @Schema(description = "HPA CPU utilization target (percentage)", example = "80", minimum = "1", maximum = "100")
        private int hpaCpuUtilization;

        @Schema(description = "HPA memory utilization target (percentage)", example = "80", minimum = "1", maximum = "100")
        private int hpaMemoryUtilization;


        
        @Override
        @JsonIgnore
        @Schema(hidden = true)
        public String getJobName() {
            return "helm_application_install";
        }

        @Override
        public Map<String, List<String>> convertToSpecificParams() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("CLUSTERNAME", List.of(this.clusterName));
            params.put("HELM_CHARTS", List.of(String.join(",", this.helmCharts)));
            params.put("ENABLE_HPA", List.of(String.valueOf(this.enableHpa)));
            params.put("HPA_MIN_REPLICAS", List.of(String.valueOf(this.hpaMinReplicas)));
            params.put("HPA_MAX_REPLICAS", List.of(String.valueOf(this.hpaMaxReplicas)));
            params.put("HPA_CPU_UTILIZATION", List.of(String.valueOf(this.hpaCpuUtilization)));
            params.put("HPA_MEMORY_UTILIZATION", List.of(String.valueOf(this.hpaMemoryUtilization)));
            // params.put("HELM_CHARTS", List.of(this.helmCharts));
            return params;
        }
    }
    
    @Getter
    @Setter
    @Schema(description = "Helm chart uninstallation job")
    public static class HelmChartUninstall extends JenkinsJobDto {
        @Schema(description = "K8s cluster name", example = "cluster01", required = true)
        private String clusterName;

        @Schema(description = "List of Helm charts to uninstall", example = "[\"nginx\"]")
        private List<String> helmCharts;
        // @Schema(description = "제거할 Helm 차트 목록", example = "nginx")
        // private String helmCharts;

        
        private String version;

        @Override
        @JsonIgnore
        @Schema(hidden = true)
        public String getJobName() {
            return "helm_application_uninstall";
        }

        @Override
        public Map<String, List<String>> convertToSpecificParams() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("CLUSTERNAME", List.of(this.clusterName));
            params.put("HELM_CHARTS", List.of(String.join(",", this.helmCharts)));
            // params.put("HELM_CHARTS", List.of(this.helmCharts));
            return params;
        }
    }
}