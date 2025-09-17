package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@ApiModel(description = "K8s Cluster information")
public class K8sClusterDto {

    @JsonProperty("resourceType")
    @ApiModelProperty(value = "리소스 타입", example = "k8s")
    private String resourceType;

    @JsonProperty("id")
    @ApiModelProperty(value = "ID", example = "k8scluster01")
    private String id;

    @JsonProperty("uid")
    @ApiModelProperty(value = "UID", example = "d34aecjebd5s73abagpg")
    private String uid;

    @JsonProperty("keyValueList")
    @ApiModelProperty(value = "기타 키-값 리스트")
    private List<KeyValue> keyValueList;

    @JsonProperty("status")
    @ApiModelProperty(value = "상태", example = "Active")
    private String status;

    @JsonProperty("createdTime")
    @ApiModelProperty(value = "생성 시각", example = "2025-09-15T23:54:30.55Z")
    private String createdTime;

    @JsonProperty("cspResourceName")
    @ApiModelProperty(value = "CSP 리소스 이름", example = "d34aecjebd5s73abagpg")
    private String cspResourceName;

    @JsonProperty("cspResourceId")
    @ApiModelProperty(value = "CSP 리소스 ID", example = "d34aecjebd5s73abagpg")
    private String cspResourceId;

    @JsonProperty("name")
    @ApiModelProperty(value = "이름", example = "k8scluster01")
    private String name;

    @JsonProperty("connectionName")
    @ApiModelProperty(value = "연결 이름", example = "aws-ap-northeast-2")
    private String connectionName;

    @JsonProperty("connectionConfig")
    @ApiModelProperty(value = "연결 설정")
    private ConnectionConfig connectionConfig;

    @JsonProperty("description")
    @ApiModelProperty(value = "설명", example = "")
    private String description;

    @JsonProperty("systemMessage")
    @ApiModelProperty(value = "시스템 메시지", example = "")
    private String systemMessage;

    @JsonProperty("label")
    @ApiModelProperty(value = "레이블")
    private Map<String, String> label;

    @JsonProperty("systemLabel")
    @ApiModelProperty(value = "시스템 레이블", example = "")
    private String systemLabel;

    @JsonProperty("version")
    @ApiModelProperty(value = "버전", example = "1.32")
    private String version;

    @JsonProperty("network")
    @ApiModelProperty(value = "네트워크 설정")
    private Network network;

    @JsonProperty("accessInfo")
    @ApiModelProperty(value = "접근 정보")
    private AccessInfo accessInfo;

    @JsonProperty("addons")
    @ApiModelProperty(value = "애드온 목록")
    private Addons addons;

    @JsonProperty("cspViewK8sClusterDetail")
    @ApiModelProperty(value = "CSP View K8s 클러스터 상세 정보")
    private CspViewK8sClusterDetail cspViewK8sClusterDetail;

    @JsonProperty("spiderViewK8sClusterDetail")
    @ApiModelProperty(value = "Spider View K8s 클러스터 상세 정보")
    private SpiderViewK8sClusterDetail spiderViewK8sClusterDetail;

    // --------------------------- nested classes ---------------------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class ConnectionConfig {
        @JsonProperty("configName")
        private String configName;
        @JsonProperty("providerName")
        private String providerName;
        @JsonProperty("driverName")
        private String driverName;
        @JsonProperty("credentialName")
        private String credentialName;
        @JsonProperty("credentialHolder")
        private String credentialHolder;
        @JsonProperty("regionZoneInfoName")
        private String regionZoneInfoName;
        @JsonProperty("regionZoneInfo")
        private RegionZoneInfo regionZoneInfo;
        @JsonProperty("regionDetail")
        private RegionDetail regionDetail;
        @JsonProperty("regionRepresentative")
        private boolean regionRepresentative;
        @JsonProperty("verified")
        private boolean verified;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class RegionZoneInfo {
        @JsonProperty("assignedRegion")
        private String assignedRegion;
        @JsonProperty("assignedZone")
        private String assignedZone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class RegionDetail {
        @JsonProperty("regionId")
        private String regionId;
        @JsonProperty("regionName")
        private String regionName;
        @JsonProperty("description")
        private String description;
        @JsonProperty("location")
        private Location location;
        @JsonProperty("zones")
        private List<String> zones;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class Location {
        @JsonProperty("display")
        private String display;
        @JsonProperty("latitude")
        private double latitude;
        @JsonProperty("longitude")
        private double longitude;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class Network {
        @JsonProperty("vNetId")
        private String vNetId;
        @JsonProperty("subnetIds")
        private List<String> subnetIds;
        @JsonProperty("securityGroupIds")
        private List<String> securityGroupIds;
        @JsonProperty("keyValueList")
        private List<KeyValue> keyValueList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class AccessInfo {
        @JsonProperty("endpoint")
        private String endpoint;
        @JsonProperty("kubeconfig")
        private String kubeconfig;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class Addons {
        @JsonProperty("keyValueList")
        private List<KeyValue> keyValueList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class KeyValue {
        @JsonProperty("key")
        private String key;
        @JsonProperty("value")
        private String value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class IID {
        @JsonProperty("NameId")
        private String nameId;
        @JsonProperty("SystemId")
        private String systemId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class NodeGroup {
        @JsonProperty("IId")
        private IID iid;
        @JsonProperty("ImageIID")
        private IID imageIID;
        @JsonProperty("VMSpecName")
        private String vmSpecName;
        @JsonProperty("RootDiskType")
        private String rootDiskType;
        @JsonProperty("RootDiskSize")
        private String rootDiskSize;
        @JsonProperty("KeyPairIID")
        private IID keyPairIID;
        @JsonProperty("OnAutoScaling")
        private boolean onAutoScaling;
        @JsonProperty("DesiredNodeSize")
        private int desiredNodeSize;
        @JsonProperty("MinNodeSize")
        private int minNodeSize;
        @JsonProperty("MaxNodeSize")
        private int maxNodeSize;
        @JsonProperty("Status")
        private String status;
        @JsonProperty("Nodes")
        private List<IID> nodes;
        @JsonProperty("KeyValueList")
        private List<KeyValue> keyValueList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class CspViewK8sClusterDetail {
        @JsonProperty("IId")
        private IID iid;
        @JsonProperty("Version")
        private String version;
        @JsonProperty("Network")
        private Network network;
        @JsonProperty("NodeGroupList")
        private List<NodeGroup> nodeGroupList;
        @JsonProperty("AccessInfo")
        private AccessInfo accessInfo;
        @JsonProperty("Addons")
        private Addons addons;
        @JsonProperty("Status")
        private String status;
        @JsonProperty("CreatedTime")
        private String createdTime;
        @JsonProperty("KeyValueList")
        private List<KeyValue> keyValueList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class SpiderViewK8sClusterDetail {
        @JsonProperty("IId")
        private IID iid;
        @JsonProperty("Version")
        private String version;
        @JsonProperty("Network")
        private Network network;
        @JsonProperty("NodeGroupList")
        private List<NodeGroup> nodeGroupList;
        @JsonProperty("AccessInfo")
        private AccessInfo accessInfo;
        @JsonProperty("Addons")
        private Addons addons;
        @JsonProperty("Status")
        private String status;
        @JsonProperty("CreatedTime")
        private String createdTime;
        @JsonProperty("KeyValueList")
        private List<KeyValue> keyValueList;
    }
}
