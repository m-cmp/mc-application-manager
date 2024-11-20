package kr.co.mcmp.externalrepo.model;

import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class DockerHubCatalog {
    private int total;
    private List<Result> results;

    @Data
    public static class Result {
        private String id;
        private String name;
        private String slug;
        private String type;
        private Publisher publisher;
        private String created_at;
        private String updated_at;
        private String short_description;
        private String source;
        private int star_count;
        private List<RatePlans> rate_plans;
        private LogoUrl logo_url;
        private boolean extension_reviewed;
        private List<Category> categories;
        private boolean archived;
    }

    @Data
    public static class Publisher {
        private String id;
        private String name;
    }

    @Data
    public static class LogoUrl {
        private String large;
        private String small;
    }

    @Data
    public static class Category {
        private String name;
        private String slug;
    }

    @Data
    public static class RatePlans {
        private String id;
        private List<Repository> repositories;
        private List<OperatingSystem> operating_systems;
        private List<Architecture> architectures;
    }

    @Data
    public static class Repository {
        private String name;
        private String namespace;
        private String description;
        private String type;
        private String pull_count;
        private boolean is_automated;
        private boolean is_official;
        private boolean is_trusted;
        private String last_pushed_at;
        private String last_pulled_at;
        private boolean archived;
    }

    @Data
    public static class OperatingSystem {
        private String name;
        private String label;
    }

    @Data
    public static class Architecture {
        private String name;
        private String label;
    }
}
