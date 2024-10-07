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

    @Schema(description = "Namespace", example = "ns01")
    private String namespace;
    @Schema(description = "MCIS 이름", example = "my-mcis")
    private String mciName;

    @JsonIgnore
    @Schema(hidden = true)
    public abstract String getJobName();
    public abstract Map<String, List<String>> convertToSpecificParams();

    public Map<String, List<String>> convertToJenkinsParams() {
        Map<String, List<String>> params = new HashMap<>();
        params.put("NAMESPACE", List.of(this.namespace));
        params.put("MCI_ID", List.of(this.mciName));
        params.putAll(convertToSpecificParams());
        return params;
    }

    @Getter
    @Setter
    @Schema(description = "VM 애플리케이션 설치 작업")
    public static class VmApplicationInstall extends JenkinsJobDto {
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
            params.put("APPLICATIONS", List.of(String.join(",", this.applications)));
            return params;
        }
    }
}