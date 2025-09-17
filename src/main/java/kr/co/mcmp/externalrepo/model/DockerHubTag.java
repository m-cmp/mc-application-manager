package kr.co.mcmp.externalrepo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class DockerHubTag {
    // Docker Hub returns "count", not "total"
    private int count;
    private String next;
    private String previous;
    private List<TagResult> results;

    @Data
    public static class TagResult {
        private int id;
        private String name;
        @JsonProperty("full_size")
        private long fullSize;
        @JsonProperty("last_updated")
        private String lastUpdated;
        @JsonProperty("last_updater_username")
        private String lastUpdaterUsername;
        private boolean v2;
        private List<Image> images;
        // 필요하면 다른 필드 추가
    }

    @Data
    public static class Image {
        private String architecture;
        private String features;
        private String variant;
        private String digest;
        private String os;
        @JsonProperty("os_features")
        private String osFeatures;
        @JsonProperty("os_version")
        private String osVersion;
        private long size;
        private String status;
        @JsonProperty("last_pulled")
        private String lastPulled;
        @JsonProperty("last_pushed")
        private String lastPushed;
    }
}
