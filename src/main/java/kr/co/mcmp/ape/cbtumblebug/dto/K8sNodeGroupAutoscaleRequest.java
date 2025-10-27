package kr.co.mcmp.ape.cbtumblebug.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class K8sNodeGroupAutoscaleRequest {
    
    @JsonProperty("desiredNodeSize")
    private String desiredNodeSize;
    
    @JsonProperty("maxNodeSize")
    private String maxNodeSize;
    
    @JsonProperty("minNodeSize")
    private String minNodeSize;
    
    // Manual 모드 지원: minNodeSize와 maxNodeSize를 null로 설정 가능
    public boolean includeAutoScalingParams() {
        return minNodeSize != null && maxNodeSize != null;
    }
}
