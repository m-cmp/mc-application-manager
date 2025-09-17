package kr.co.mcmp.ape.cbtumblebug.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@ApiModel(description = "VM Spec information")
public class VmSpecDto {

    @JsonProperty("vmspec")
    @ApiModelProperty(value = "VM spec list")
    private List<VmSpecInfo> vmspec;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @ApiModel(description = "VM Spec Info")
    public static class VmSpecInfo {
        @JsonProperty("Region")
        @ApiModelProperty(value = "Region", example = "ap-northeast-2")
        private String region;

        @JsonProperty("Name")
        @ApiModelProperty(value = "Spec name", example = "m7i.16xlarge")
        private String name;

        @JsonProperty("VCpu")
        @ApiModelProperty(value = "vCPU information")
        private VCpuInfo vCpu;

        @JsonProperty("MemSizeMib")
        @ApiModelProperty(value = "Memory size in MiB", example = "262144")
        private String memSizeMib;

        @JsonProperty("DiskSizeGB")
        @ApiModelProperty(value = "Disk size in GB", example = "-1")
        private String diskSizeGB;

        @JsonProperty("KeyValueList")
        @ApiModelProperty(value = "Key-value list")
        private List<KeyValue> keyValueList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @ApiModel(description = "vCPU information")
    public static class VCpuInfo {
        @JsonProperty("Count")
        @ApiModelProperty(value = "vCPU count", example = "64")
        private String count;

        @JsonProperty("ClockGHz")
        @ApiModelProperty(value = "Clock speed in GHz", example = "3.2")
        private String clockGHz;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @ApiModel(description = "Key-value pair")
    public static class KeyValue {
        @JsonProperty("key")
        @ApiModelProperty(value = "Key")
        private String key;

        @JsonProperty("value")
        @ApiModelProperty(value = "Value")
        private String value;
    }
}
