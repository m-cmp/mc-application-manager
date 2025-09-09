package kr.co.mcmp.externalrepo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public class ArtifactHubPackage {

    private List<Package> packages;

    @Data
    public static class Package {

        private String package_id;
        private String name;
        private String normalized_name;
        private String description;
        private boolean has_values_schema; // 기존 String -> boolean으로 수정
        private Repository repository;
        private Category category; // 새로 추가한 필드


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

            public int getId() {
                return id;
            }

            public String getDisplayName() {
                return displayName;
            }

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
    public static class Repository {
        private String url;
        private String name;
        private boolean official;
        private String display_name;
        private String organization_display_name;
    }
}
