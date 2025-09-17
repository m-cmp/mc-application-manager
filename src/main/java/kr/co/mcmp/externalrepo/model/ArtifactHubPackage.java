package kr.co.mcmp.externalrepo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class ArtifactHubPackage {

    private List<Package> packages;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Package {
        @JsonAlias({"package_id", "packageId"})
        private String packageId;
        private String name;
        @JsonAlias({"normalized_name", "normalizedName"})
        private String normalizedName;
        private String description;
        @JsonAlias({"has_values_schema", "hasValuesSchema"})
        private boolean hasValuesSchema;
        private boolean deprecated;
        private boolean official;
        private String version;
        @JsonAlias({"app_version", "appVersion"})
        private String appVersion;
        private String license;
        private int stars;
        @JsonAlias({"security_report_summary", "securityReportSummary"})
        private SecurityReportSummary securityReportSummary;
        @JsonAlias({"all_containers_images_whitelisted", "allContainersImagesWhitelisted"})
        private boolean allContainersImagesWhitelisted;
        @JsonAlias({"production_organizations_count", "production-organizationsCount"})
        private int productionOrganizationsCount;
        private long ts;
        private Repository repository;
        private Category category;

        // enum으로 정의한 카테고리
        public enum Category {
            AI_MACHINE_LEARNING(1, "AI / Machine learning"),
            DATABASE(2, "Database"),
            INTEGRATION_AND_DELIVERY(3, "Integration and delivery"),
            MONITORING_AND_LOGGING(4, "Monitoring and logging"),
            NETWORKING(5, "Networking"),
            SECURITY(6, "Security"),
            STORAGE(7, "Storage"),
            STREAMING_AND_MESSAGING(8, "Streaming and messaging");

            private final int id;
            private final String displayName;

            Category(int id, String displayName) {
                this.id = id;
                this.displayName = displayName;
            }

            public int getId() { return id; }
            public String getDisplayName() { return displayName; }

            @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
            public static Category fromId(int id) {
                for (Category c : values()) {
                    if (c.id == id) return c;
                }
                return null;
            }

            public static Category fromName(String name) {
                for (Category c : values()) {
                    if (c.displayName.equalsIgnoreCase(name)) return c;
                }
                return null;
            }
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        private String url;
        private String name;
        private boolean official;
        @JsonAlias({"display_name", "displayName"})
        private String displayName;
        @JsonAlias({"organization_display_name", "organizationDisplayName"})
        private String organizationDisplayName;
        @JsonAlias({"repository_id", "repositoryId"})
        private String repositoryId;
        @JsonAlias({"scanner_disabled", "scannerDisabled"})
        private boolean scannerDisabled;
        @JsonAlias({"organization_name", "organizationName"})
        private String organizationName;
        @JsonAlias({"verified_publisher", "verifiedPublisher"})
        private boolean verifiedPublisher;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SecurityReportSummary {
        private int low;
        private int medium;
        private int high;
        private int critical;
        private int unknown;
    }
}
