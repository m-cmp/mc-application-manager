package kr.co.mcmp.dto.oss;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
public class NexusRepositoryDto {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResGetRepositoryDto {
        @Schema(title = "레포지토리 이름", required = true)
        @NotBlank
        private String name;

        @Schema(title = "레포지토리 포맷 유형", required = true)
        @NotBlank
        private String format;

        @Schema(title = "레포지토리 타입 유형", required = true)
        @NotBlank
        private String type;

        @Schema(title = "레포지토리 접근 url", required = true)
        @NotBlank
        private String url;

        @Schema(title = "레포지토리 사용자 접근 가능 여부", required = true)
        @NotNull
        private Boolean online;

        @Valid
        private ResGetStorageDto storage;

        @Valid
        private ResGetDockerDto docker;

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ResGetStorageDto {
            @Schema(title = "아티팩트를 저장하는 물리적 저장소 이름", required = true, example = "default")
            @NotBlank
            private String blobStoreName;

            @Schema(title = "저장되는 아티팩트 유형 일치 여부 검증", required = true)
            @NotNull
            private Boolean strictContentTypeValidation;

            @Schema(title = "레포지토리 읽기/쓰기 설정", required = true, example = "allow, allow_once, read_only")
            @NotBlank
            private String writePolicy;
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ResGetDockerDto {
            @Schema(title = "도커 registry 버전 지원(false: v2 지원)", required = true)
            @NotNull
            private Boolean v1Enabled;

            @Schema(title = "도커 클라이언트가 레포지토리에 접근할 때 기본 인증 사용 여부", required = true)
            @NotNull
            private Boolean forceBasicAuth;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 http 포트")
            private Integer httpPort;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 https 포트")
            private Integer httpsPort;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 서브도메인")
            private String subdomain;
        }
    }

    @Getter
    public static class ReqCreateRepositoryDto {
        @Schema(title = "레포지토리 이름", required = true)
        @NotBlank
        private String name;

        @Schema(title = "레포지토리 사용자 접근 가능 여부", required = true)
        @NotNull
        private Boolean online;

        @Valid
        private ReqCreateStorageDto storage;

        @Valid
        private ReqCreateDockerDto docker;

        @Getter
        public static class ReqCreateStorageDto {
            @Schema(title = "아티팩트를 저장하는 물리적 저장소 이름", required = true, example = "default")
            @NotBlank
            private String blobStoreName;

            @Schema(title = "저장되는 아티팩트 유형 일치 여부 검증", required = true)
            @NotNull
            private Boolean strictContentTypeValidation;

            @Schema(title = "레포지토리 읽기/쓰기 설정", required = true, example = "allow, allow_once, read_only")
            @NotBlank
            private String writePolicy;
        }

        @Getter
        public static class ReqCreateDockerDto {
            @Schema(title = "도커 registry 버전 지원(false: v2 지원)", required = true)
            @NotNull
            private Boolean v1Enabled;

            @Schema(title = "도커 클라이언트가 레포지토리에 접근할 때 기본 인증 사용 여부", required = true)
            @NotNull
            private Boolean forceBasicAuth;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 http 포트")
            private Integer httpPort;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 https 포트")
            private Integer httpsPort;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 서브도메인")
            private String subdomain;
        }
    }

    @Getter
    public static class ReqUpdateRepositoryDto {
        @Schema(title = "레포지토리 이름", required = true)
        @JsonIgnore
        private String name;

        @Schema(title = "레포지토리 사용자 접근 가능 여부", required = true)
        @NotNull
        private Boolean online;

        @Valid
        private ReqUpdateStorageDto storage;

        @Valid
        private ReqUpdateDockerDto docker;

        @Getter
        public static class ReqUpdateStorageDto {
            @Schema(title = "아티팩트를 저장하는 물리적 저장소 이름", required = true, example = "default")
            @JsonIgnore
            private String blobStoreName;

            @Schema(title = "저장되는 아티팩트 유형 일치 여부 검증", required = true)
            @NotNull
            private Boolean strictContentTypeValidation;

            @Schema(title = "레포지토리 읽기/쓰기 설정", required = true, example = "allow, allow_once, read_only")
            @NotBlank
            private String writePolicy;
        }

        @Getter
        public static class ReqUpdateDockerDto {
            @Schema(title = "도커 registry 버전 지원(false: v2 지원)", required = true)
            @NotNull
            private Boolean v1Enabled;

            @Schema(title = "도커 클라이언트가 레포지토리에 접근할 때 기본 인증 사용 여부", required = true)
            @NotNull
            private Boolean forceBasicAuth;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 http 포트")
            private Integer httpPort;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 https 포트")
            private Integer httpsPort;

            @Schema(title = "도커 레포지토리에 접근할 때 사용할 서브도메인")
            private String subdomain;
        }
    }
}
