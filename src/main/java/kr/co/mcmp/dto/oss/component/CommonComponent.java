package kr.co.mcmp.dto.oss.component;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Getter
public class CommonComponent {

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ComponentDto {

        @Schema(title = "컴포넌트 식별자", required = true, example = "dGTzdKenZQCzMlY8Ne")
        @NotBlank
        private String id;

        @Schema(title = "컴포넌트가 속한 저장소", required = true, example = "repo")
        @NotBlank
        private String repository;

        @Schema(title = "컴포넌트 포맷 유형", required = true, example = "raw, helm, docker")
        @NotBlank
        private String format;

        @Schema(title = "컴포넌트가 속한 그룹", required = true, example = "hosted")
        @NotBlank
        private String group;

        @Schema(title = "컴포넌트 이름", required = true, example = "comp")
        @NotBlank
        private String name;

        @Valid
        private List<AssetsDto> assets;

        @Getter
        @Builder
        @AllArgsConstructor
        @NoArgsConstructor
        public static class AssetsDto {

            @Schema(title = "다운로드 URL", required = true, example = "http://127.0.0.1:8080/repository/repo/comp.zip")
            @NotBlank
            private String downloadUrl;

            @Schema(title = "자원 식별자", required = true, example = "dGTzdKenZQCzMlY8Ne")
            @NotBlank
            private String id;

            @Schema(title = "컨텐츠 타입", required = true, example = "application/zip")
            @NotBlank
            private String contentType;

            @Schema(title = "파일 크기", required = true, example = "1000")
            private Integer fileSize;

            @Schema(title = "업로드 날짜", required = true, example = "2024-01-01T00:00:00.188+00:00")
            private String blobCreated;
        }
    }
}
