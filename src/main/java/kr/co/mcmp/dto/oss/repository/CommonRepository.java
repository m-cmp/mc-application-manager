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

        @Schema(title = "레포지토리 이름", required = true, example = "repo")
        @NotBlank
        private String name;

        @Schema(title = "레포지토리 포맷 유형", required = true, example = "raw, helm, docker")
        @NotBlank
        private String format;

        @Schema(title = "레포지토리 타입 유형", required = true, example = "hosted")
        @NotBlank
        private String type;

        @Schema(title = "레포지토리 접근 url", required = true, example = "등록: 빈값, 수정: 값")
        @NotNull
        private String url;

        @Schema(title = "레포지토리 사용자 접근 가능 여부", required = true)
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

            @Schema(title = "아티팩트를 저장하는 물리적 저장소 이름", required = true, example = "default")
            private String blobStoreName;

            @Schema(title = "저장되는 아티팩트 유형 일치 여부 검증", required = true)
            private Boolean strictContentTypeValidation;

            @Schema(title = "레포지토리 읽기/쓰기 설정", required = true, example = "allow, allow_once, deny")
            private String writePolicy;
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @ToString
        public static class DockerDto {

            @Schema(title = "도커 registry 버전 지원(false: v2 지원)", required = true)
            private Boolean v1Enabled;

            @Schema(title = "도커 클라이언트가 레포지토리에 접근할 때 기본 인증 사용 여부", required = true)
            private Boolean forceBasicAuth;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 http 포트", example = "8080")
            private Integer httpPort;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 https 포트", example = "9090")
            private Integer httpsPort;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 서브도메인", example = "/test")
            private String subdomain;
        }
    }
}
