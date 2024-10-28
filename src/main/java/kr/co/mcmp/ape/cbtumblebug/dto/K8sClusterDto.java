package kr.co.mcmp.ape.cbtumblebug.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "K8s Cluster 정보")
public class K8sClusterDto {

    @ApiModelProperty(value = "연결 이름", example = "alibaba-ap-northeast-2")
    private String connectionName;

    @ApiModelProperty(value = "CSP 리소스 ID", example = "csp-06eb41e14121c550a")
    private String cspResourceId;

    @ApiModelProperty(value = "CSP 리소스 이름", example = "we12fawefadf1221edcf")
    private String cspResourceName;

    @ApiModelProperty(value = "CSP View K8s 클러스터 상세 정보")
    @JsonProperty("CspViewK8sClusterDetail")
    private CspViewK8sClusterDetail cspViewK8sClusterDetail;

    @ApiModelProperty(value = "설명", example = "My K8sCluster")
    private String description;

    @ApiModelProperty(value = "ID", example = "aws-ap-southeast-1")
    private String id;

    @ApiModelProperty(value = "레이블")
    private Map<String, String> label;

    @ApiModelProperty(value = "이름", example = "aws-ap-southeast-1")
    private String name;

    @ApiModelProperty(value = "리소스 타입")
    private String resourceType;

    @ApiModelProperty(value = "시스템 레이블", example = "Managed by CB-Tumblebug")
    private String systemLabel;

    @ApiModelProperty(value = "시스템 메시지", example = "Failed because ...")
    private String systemMessage;

    @ApiModelProperty(value = "UID", example = "wef12awefadf1221edcf")
    private String uid;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ApiModel(description = "CSP View K8s 클러스터 상세 정보")
    public static class CspViewK8sClusterDetail {
        @ApiModelProperty(value = "IID 정보")
        @JsonProperty("IId")
        private IID iid;
    
        @ApiModelProperty(value = "버전")
        @JsonProperty("Version")
        private String version;
    
        @ApiModelProperty(value = "네트워크 정보")
        @JsonProperty("Network")
        private Network network;
    
        @ApiModelProperty(value = "노드 그룹 목록")
        @JsonProperty("NodeGroupList")
        private List<NodeGroup> nodeGroupList;
    
        @ApiModelProperty(value = "접근 정보")
        @JsonProperty("AccessInfo")
        private AccessInfo accessInfo;
    
        @ApiModelProperty(value = "애드온 정보")
        @JsonProperty("Addons")
        private Addons addons;
    
        @ApiModelProperty(value = "상태")
        @JsonProperty("Status")
        private String status;
    
        @ApiModelProperty(value = "생성 시간")
        @JsonProperty("CreatedTime")
        private String createdTime;
    
        @ApiModelProperty(value = "키-값 목록")
        @JsonProperty("KeyValueList")
        private List<KeyValue> keyValueList;
    
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ApiModel(description = "접근 정보")
    public static class AccessInfo {
        @ApiModelProperty(value = "엔드포인트")
        private String endpoint;

        @ApiModelProperty(value = "kubeconfig")
        private String kubeconfig;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ApiModel(description = "애드온 정보")
    public static class Addons {
        @ApiModelProperty(value = "키-값 목록")
        private List<KeyValue> keyValueList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ApiModel(description = "IID 정보")
    public static class IID {
        @JsonProperty("NameId")
        @ApiModelProperty(value = "이름 ID")
        private String nameId;

        @ApiModelProperty(value = "시스템 ID")
        @JsonProperty("SystemId")
        private String systemId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ApiModel(description = "키-값 정보")
    public static class KeyValue {
        @ApiModelProperty(value = "키")
        private String key;

        @ApiModelProperty(value = "값")
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ApiModel(description = "네트워크 정보")
    public static class Network {
        @ApiModelProperty(value = "키-값 목록")
        private List<KeyValue> keyValueList;

        @ApiModelProperty(value = "보안 그룹 IID 목록")
        private List<IID> securityGroupIIDs;

        @ApiModelProperty(value = "서브넷 IID 목록")
        private List<IID> subnetIIDs;

        @ApiModelProperty(value = "VPC IID")
        private IID vpcIID;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ApiModel(description = "노드 그룹 정보")
    public static class NodeGroup {
        
        @ApiModelProperty(value = "원하는 노드 크기")
        @JsonProperty("DesiredNodeSize")
        private int desiredNodeSize;
    
        @ApiModelProperty(value = "IID")
        @JsonProperty("IId")
        private IID iid;
    
        @ApiModelProperty(value = "이미지 IID")
        @JsonProperty("ImageIID")
        private IID imageIID;
    
        @ApiModelProperty(value = "키 페어 IID")
        @JsonProperty("KeyPairIID")
        private IID keyPairIID;
    
        @ApiModelProperty(value = "키-값 목록")
        @JsonProperty("KeyValueList")
        private List<KeyValue> keyValueList;
    
        @ApiModelProperty(value = "최대 노드 크기")
        @JsonProperty("MaxNodeSize")
        private int maxNodeSize;
    
        @ApiModelProperty(value = "최소 노드 크기")
        @JsonProperty("MinNodeSize")
        private int minNodeSize;
    
        @ApiModelProperty(value = "노드 목록")
        @JsonProperty("Nodes")
        private List<IID> nodes;
    
        @ApiModelProperty(value = "오토스케일링 여부")
        @JsonProperty("OnAutoScaling")
        private boolean onAutoScaling;
    
        @ApiModelProperty(value = "루트 디스크 크기")
        @JsonProperty("RootDiskSize")
        private String rootDiskSize;
    
        @ApiModelProperty(value = "루트 디스크 타입")
        @JsonProperty("RootDiskType")
        private String rootDiskType;
    
        @ApiModelProperty(value = "상태", example = "Creating")
        @JsonProperty("Status")
        private String status;
    
        @ApiModelProperty(value = "VM 스펙 이름")
        @JsonProperty("VMSpecName")
        private String vmSpecName;
    }
}