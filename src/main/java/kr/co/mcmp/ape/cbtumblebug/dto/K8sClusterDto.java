package kr.co.mcmp.ape.cbtumblebug.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "K8s Cluster 정보")
public class K8sClusterDto {

    @JsonProperty("resourceType")
    @ApiModelProperty(value = "리소스 타입", example = "k8s")
    private String resourceType;

    @JsonProperty("id")
    @ApiModelProperty(value = "ID", example = "pmk01")
    private String id;

    @JsonProperty("uid")
    @ApiModelProperty(value = "UID", example = "csv8gvbebd5s7398msr0")
    private String uid;

    @JsonProperty("cspResourceName")
    @ApiModelProperty(value = "CSP 리소스 이름", example = "csv8gvbebd5s7398msr0")
    private String cspResourceName;

    @JsonProperty("cspResourceId")
    @ApiModelProperty(value = "CSP 리소스 ID", example = "81b5ace4-1f9e-4416-8862-31387ac38e6a")
    private String cspResourceId;

    @JsonProperty("name")
    @ApiModelProperty(value = "이름", example = "pmk01")
    private String name;

    @JsonProperty("connectionName")
    @ApiModelProperty(value = "연결 이름", example = "nhncloud-kr1")
    private String connectionName;

    @JsonProperty("connectionConfig")
    @ApiModelProperty(value = "연결 설정")
    private ConnectionConfig connectionConfig;

    @JsonProperty("description")
    @ApiModelProperty(value = "설명", example = "NHN Cloud Kubernetes Cluster & Workflow Created cluster")
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

    @JsonProperty("CspViewK8sClusterDetail")
    @ApiModelProperty(value = "CSP View K8s 클러스터 상세 정보")
    private CspViewK8sClusterDetail cspViewK8sClusterDetail;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
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
    public static class RegionZoneInfo {
        @JsonProperty("assignedRegion")
        private String assignedRegion;

        @JsonProperty("assignedZone")
        private String assignedZone;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
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
    public static class IID {
        @JsonProperty("NameId")
        private String nameId;

        @JsonProperty("SystemId")
        private String systemId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Network {
        @JsonProperty("VpcIID")
        private IID vpcIID;

        @JsonProperty("SubnetIIDs")
        private List<IID> subnetIIDs;

        @JsonProperty("SecurityGroupIIDs")
        private List<IID> securityGroupIIDs;

        @JsonProperty("KeyValueList")
        private List<KeyValue> keyValueList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
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
    public static class AccessInfo {
        @JsonProperty("Endpoint")
        private String endpoint;

        @JsonProperty("Kubeconfig")
        private String kubeconfig;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Addons {
        @JsonProperty("KeyValueList")
        private List<KeyValue> keyValueList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyValue {
        @JsonProperty("key")
        private String key;

        @JsonProperty("value")
        private String value;
    }
}
