package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MciAccessInfoDto {
    @JsonProperty("MciId")
    @JsonAlias("InfraId")
    private String mciId;
    
    @JsonProperty("MciSubGroupAccessInfo")
    @JsonAlias("InfraNodeGroupAccessInfo")
    private List<MciSubGroupAccessInfo> mciSubGroupAccessInfo;

    @Data
    public static class MciSubGroupAccessInfo {
        @JsonProperty("SubGroupId")
        @JsonAlias("NodeGroupId")
        private String subGroupId;
        
        @JsonProperty("BastionVmId")
        @JsonAlias("BastionNodeId")
        private String bastionVmId;
        
        @JsonProperty("MciVmAccessInfo")
        @JsonAlias("NodeAccessInfo")
        private List<MciVmAccessInfo> mciVmAccessInfo;
    }

    @Data
    public static class MciVmAccessInfo {
        @JsonProperty("vmId")
        @JsonAlias("nodeId")
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
