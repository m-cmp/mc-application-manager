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
@Schema(description = "Jenkins 작업 DTO")
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
    @Schema(description = "VM 애플리케이션 설치 작업")
    public static class VmApplicationInstall extends JenkinsJobDto {
        @Schema(description = "설치할 애플리케이션 목록", example = "nginx")
        private List<String> applications;

        @Schema(description = "설치할 VM", example = "vm01")
        private String vmName;

        @Schema(description = "MCIS 이름", example = "mci01")
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
    @Schema(description = "VM 애플리케이션 삭제 작업")
    public static class VmApplicationUninstall extends JenkinsJobDto {
        @Schema(description = "삭제 애플리케이션 목록", example = "nginx")
        private List<String> applications;

        @Schema(description = "MCIS 이름", example = "mci01")
        private String mciName;

        @Schema(description = "설치할 VM", example = "vm01")
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
    @Schema(description = "Helm 차트 설치 작업")
    public static class HelmChartInstall extends JenkinsJobDto {
        @Schema(description = "K8s 클러스터 이름", example = "cluster01", required = true)
        private String clusterName;

        @Schema(description = "설치할 Helm 차트 목록", example = "[\"nginx\", \"grafana\"]")
        private List<String> helmCharts;
        // @Schema(description = "설치할 Helm 차트", example = "nginx")
        // private String helmCharts;
        private String version;
        
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
            // params.put("HELM_CHARTS", List.of(this.helmCharts));
            return params;
        }
    }
    
    @Getter
    @Setter
    @Schema(description = "Helm 차트 제거 작업")
    public static class HelmChartUninstall extends JenkinsJobDto {
        @Schema(description = "K8s 클러스터 이름", example = "cluster01", required = true)
        private String clusterName;

        @Schema(description = "제거할 Helm 차트 목록", example = "[\"nginx\", \"grafana\"]")
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