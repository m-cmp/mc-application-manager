package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class K8sClusterResponse {
    private List<K8sClusterDto> k8sClusterInfo;
}