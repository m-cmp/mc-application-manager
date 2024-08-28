package kr.co.mcmp.dto.oss.component;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommonUploadComponent {

    @Schema(title = "디렉토리 이름", required = true, example = "test")
    @NotBlank
    private String directory;

    @Schema(title = "파일", required = true)
    @Valid
    private List<FilesDto> assets;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FilesDto {

        @Schema(title = "파일")
        private MultipartFile file;

        @Schema(title = "파일 이름", example = "test")
        private String filename;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TextComponentDto {

        @Schema(title = "파일명", required = true, example = "test")
        @NotBlank
        private String filename;

        @Schema(title = "파일 확장자", required = true, example = "txt, sh, yaml")
        @NotBlank
        private String extension;

        @Schema(title = "파일 경로", required = true, example = "/")
        @NotBlank
        private String directory;

        @Schema(title = "텍스트 내용", required = true, example = "test")
        @NotBlank
        private String text;
    }
}
