package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class K8sSpec {
    @JsonProperty("Region")
    private String region;
    
    @JsonProperty("Name")
    private String name;

    @JsonProperty("VCpu")
    private VCpu vCpu;

    @JsonProperty("Mem")
    private String mem;
    
    @JsonProperty("Gpu")
    private Object gpu;

    @JsonProperty("KeyValueList")
    private List<KeyValue> keyValueList;

    @Data
    public static class VCpu {
        @JsonProperty("Count")
        private String count;
        
        @JsonProperty("Clock")
        private String clock;
    }

    @Data
    public static class KeyValue {
        @JsonProperty("key")
        private String key;
        
        @JsonProperty("value")
        private String value;
    }
}