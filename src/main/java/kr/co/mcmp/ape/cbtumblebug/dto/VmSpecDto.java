package kr.co.mcmp.ape.cbtumblebug.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Schema(description = "VM Spec information")
public class VmSpecDto {

    @JsonProperty("vmspec")
    @Schema(description = "VM spec list")
    private List<VmSpecInfo> vmspec;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @Schema(description = "VM Spec Info")
    public static class VmSpecInfo {
        @JsonProperty("Region")
        @Schema(description = "Region", example = "ap-northeast-2")
        private String region;

        @JsonProperty("Name")
        @Schema(description = "Spec name", example = "m7i.16xlarge")
        private String name;

        @JsonProperty("VCpu")
        @Schema(description = "vCPU information")
        private VCpuInfo vCpu;

        @JsonProperty("MemSizeMib")
        @Schema(description = "Memory size in MiB", example = "262144")
        private String memSizeMib;

        @JsonProperty("DiskSizeGB")
        @Schema(description = "Disk size in GB", example = "-1")
        private String diskSizeGB;

        @JsonProperty("KeyValueList")
        @Schema(description = "Key-value list")
        private List<KeyValue> keyValueList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @Schema(description = "vCPU information")
    public static class VCpuInfo {
        @JsonProperty("Count")
        @Schema(description = "vCPU count", example = "64")
        private String count;

        @JsonProperty("ClockGHz")
        @Schema(description = "Clock speed in GHz", example = "3.2")
        private String clockGHz;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @Schema(description = "Key-value pair")
    public static class KeyValue {
        @JsonProperty("key")
        @Schema(description = "Key")
        private String key;

        @JsonProperty("value")
        @Schema(description = "Value")
        private String value;
    }
}
