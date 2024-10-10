package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class K8sClusterResponse {
     @JsonProperty("K8sClusterInfo")
    private List<K8sClusterDto> k8sClusterInfo;
}