package kr.co.mcmp.dto.oss.repository;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Data
public class CommonRepository {

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class RepositoryDto {

        @Schema(title = "Repository name", required = true, example = "repo")
        @NotBlank
        private String name;

        @Schema(title = "Repository format type", required = true, example = "raw, helm, docker")
        @NotBlank
        private String format;

        @Schema(title = "Repository type", required = true, example = "hosted")
        @NotBlank
        private String type;

        @Schema(title = "Repository access URL", required = true, example = "Registration: empty, Modification: value")
        @NotNull
        private String url;

        @Schema(title = "Repository user access availability", required = true)
        @NotNull
        private Boolean online;

        private StorageDto storage;

        private DockerDto docker;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @ToString
        public static class StorageDto {

            @Schema(title = "Physical storage name for artifacts", required = true, example = "default")
            private String blobStoreName;

            @Schema(title = "Validate artifact type matching", required = true)
            private Boolean strictContentTypeValidation;

            @Schema(title = "Repository read/write policy", required = true, example = "allow, allow_once, deny")
            private String writePolicy;
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @ToString
        public static class DockerDto {

            @Schema(title = "Docker registry version support (false: v2 support)", required = true)
            private Boolean v1Enabled;

            @Schema(title = "Use basic authentication when Docker client accesses repository", required = true)
            private Boolean forceBasicAuth;

            @Schema(title = "HTTP port for Docker repository access", example = "8080")
            private Integer httpPort;

            @Schema(title = "HTTPS port for Docker repository access", example = "9090")
            private Integer httpsPort;

            @Schema(title = "Subdomain for Docker repository access", example = "/test")
            private String subdomain;
        }
    }
}
