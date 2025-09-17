package kr.co.mcmp.softwarecatalog.application.dto;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DockerHubImageRegistrationRequest {

    private String id;
    private String name;
    private String tag;
    private String slug;
    private String type;
    private Publisher publisher;
    private String shortDescription;
    private String source;
    private Boolean archived;
    private String sourceType;
    private int starCount;
    private List<Category> categories;
    private List<RatePlan> ratePlans;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Publisher {
        private String id;
        private String name;
        private String slug;
        private String type;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Category {
        private String name;
        private String slug;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RatePlan {
        private String id;
        private String name;
        private String slug;
        private String type;
        private List<Architectures> architectures;
        private List<Repository> repositories;
        private List<OperatingSystem> operating_systems;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Architectures {
        private String name;
        private String label;
    }



    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Repository {
        private String id;
        private String name;
        private String namespace;
        private String description;
        private String type;
        private String pull_count; // "1B+" 같은 문자열 그대로 저장
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OperatingSystem {
        private String name;
        private String label;
    }

}