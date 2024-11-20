package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MciAccessInfoDto {
    @JsonProperty("MciId")
    private String mciId;
    
    @JsonProperty("MciSubGroupAccessInfo")
    private List<MciSubGroupAccessInfo> mciSubGroupAccessInfo;

    @Data
    public static class MciSubGroupAccessInfo {
        @JsonProperty("SubGroupId")
        private String subGroupId;
        
        @JsonProperty("BastionVmId")
        private String bastionVmId;
        
        @JsonProperty("MciVmAccessInfo")
        private List<MciVmAccessInfo> mciVmAccessInfo;
    }

    @Data
    public static class MciVmAccessInfo {
        @JsonProperty("vmId")
        private String vmId;
        
        @JsonProperty("publicIP")
        private String publicIP;
        
        @JsonProperty("privateIP")
        private String privateIP;
        
        @JsonProperty("sshPort")
        private String sshPort;
        
        @JsonProperty("privateKey")
        private String privateKey;
    }
}