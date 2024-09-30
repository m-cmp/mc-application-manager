package kr.co.mcmp.ape.dto.reqDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Schema(description = "Jenkins 작업 DTO")
public abstract class JenkinsJobDto {

    @Schema(description = "CB-Tumblebug Swagger URI", example = "http://localhost:1323/tumblebug")
    private String cbTumblebugSwaggerUri;
    @Schema(description = "MCIS 이름", example = "my-mcis")
    private String mcisName;

    @JsonIgnore
    @Schema(hidden = true)
    public abstract String getJobName();
    public abstract Map<String, List<String>> convertToSpecificParams();

    public Map<String, List<String>> convertToJenkinsParams() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("CB_TUMBLEBUG_SWAGGER_URI", List.of(this.cbTumblebugSwaggerUri));
        params.put("MCIS_NAME", List.of(this.mcisName));
        params.putAll(convertToSpecificParams());
        return params;
    }


    @Getter
    @Setter
    @Schema(description = "VM 애플리케이션 설치 작업")
    public static class VmApplicationInstall extends JenkinsJobDto {
        @Schema(description = "사용자 이름", example = "admin")
        private String user;
        @Schema(description = "사용자 비밀번호", example = "password")
        private String userpass;
        @Schema(description = "설치할 애플리케이션 목록", example = "[\"nginx\", \"mysql\"]")
        private List<String> applications;

        @Override
        @JsonIgnore
        @Schema(hidden = true)
        public String getJobName() {
            return "vm_application_install";
        }

        @Override
        public Map<String, List<String>> convertToSpecificParams() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("USER", List.of(this.user));
            params.put("USERPASS", List.of(this.userpass));
            params.put("APPLICATIONS", List.of(String.join(",", this.applications)));
            return params;
        }

    }

    @Getter
    @Setter
    @Schema(description = "VM 애플리케이션 제거 작업")
    public static class VmApplicationUninstall extends JenkinsJobDto {
        @Schema(description = "사용자 이름", example = "admin")
        private String user;
        @Schema(description = "사용자 비밀번호", example = "password")
        private String userpass;
        @Schema(description = "설치할 애플리케이션 목록", example = "[\"nginx\", \"mysql\"]")
        private List<String> applications;

        @Override
        @JsonIgnore
        @Schema(hidden = true)
        public String getJobName() {
            return "vm_application_uninstall";
        }

        @Override
        public Map<String, List<String>> convertToSpecificParams() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("USER", List.of(this.user));
            params.put("USERPASS", List.of(this.userpass));
            params.put("APPLICATIONS", List.of(String.join(",", this.applications)));
            return params;
        }

    }

    @Getter
    @Setter
    @Schema(description = "Kubernetes Helm 차트 설치 작업")
    public static class KubernetesHelmInstall extends JenkinsJobDto {
        
        @Schema(description = "Kubernetes 네임스페이스", example = "default")
        private String namespace;
        @Schema(description = "클라우드 연결 이름", example = "aws-conn-01")
        private String cloudConnectionName;
        @Schema(description = "Helm 릴리스 이름", example = "my-release")
        private String helmReleaseName;
        @Schema(description = "Helm 차트 이름", example = "stable/mysql")
        private String helmChartName;
        @Schema(description = "Helm 차트 버전", example = "1.6.3")
        private String helmChartVersion;

        @Override
        @JsonIgnore
        @Schema(hidden = true)
        public String getJobName() {
            return "kubernetes_helm_install";
        }

        @Override
        public Map<String, List<String>> convertToSpecificParams() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("NAMESPACE", List.of(this.namespace));
            params.put("CLOUD_CONNECTION_NAME", List.of(this.cloudConnectionName));
            params.put("HELM_RELEASE_NAME", List.of(this.helmReleaseName));
            params.put("HELM_CHART_NAME", List.of(this.helmChartName));
            params.put("HELM_CHART_VERSION", List.of(this.helmChartVersion));
            return params;
        }

    }

    @Getter
    @Setter
    @Schema(description = "Kubernetes Helm 릴리스 제거 작업")
    public static class KubernetesHelmUninstall extends JenkinsJobDto {
        @Schema(description = "Kubernetes 네임스페이스", example = "default")
        private String namespace;
        @Schema(description = "클라우드 연결 이름", example = "aws-conn-01")
        private String cloudConnectionName;
        @Schema(description = "Helm 릴리스 이름", example = "my-release")
        private String helmReleaseName;

        @Override
        @JsonIgnore
        @Schema(hidden = true)
        public String getJobName() {
            return "kubernetes_helm_uninstall";
        }

        @Override
        public Map<String, List<String>> convertToSpecificParams() {
            Map<String, List<String>> params = new HashMap<>();
            params.put("NAMESPACE", List.of(this.namespace));
            params.put("CLOUD_CONNECTION_NAME", List.of(this.cloudConnectionName));
            params.put("HELM_RELEASE_NAME", List.of(this.helmReleaseName));
            return params;
        }
    }
}