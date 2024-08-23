package kr.co.mcmp.oss.dto;

import kr.co.mcmp.oss.entity.Oss;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OssDto {

    private Long ossIdx;
    private Long ossTypeIdx;
    private String ossName;
    private String ossDesc;
    private String ossUrl;
    private String ossUsername;
    private String ossPassword;

    // from : 외부 (entity -> dto)
    public static OssDto from(Oss oss) {
        return OssDto.builder()
                .ossIdx(oss.getOssIdx())
                .ossTypeIdx(oss.getOssType().getOssTypeIdx())
                .ossName(oss.getOssName())
                .ossDesc(oss.getOssDesc())
                .ossUrl(oss.getOssUrl())
                .ossUsername(oss.getOssUsername())
                .ossPassword(oss.getOssPassword())
                .build();
    }

    // of : 내부 (dto -> dto)
    public static OssDto of(OssDto ossDto) {
        return OssDto.builder()
                .ossIdx(ossDto.getOssIdx())
                .ossTypeIdx(ossDto.getOssTypeIdx())
                .ossName(ossDto.getOssName())
                .ossDesc(ossDto.getOssDesc())
                .ossUrl(ossDto.getOssUrl())
                .ossUsername(ossDto.getOssUsername())
                .ossPassword(ossDto.getOssPassword())
                .build();
    }

    // toEntity : Entity 변환 (dto -> entity)
    public static Oss toEntity(OssDto ossDto, OssTypeDto ossTypeDto) {
        return Oss.builder()
                .ossIdx(ossDto.getOssIdx())
                .ossType(OssTypeDto.toEntity(ossTypeDto))
                .ossName(ossDto.getOssName())
                .ossDesc(ossDto.getOssDesc())
                .ossUrl(ossDto.getOssUrl())
                .ossUsername(ossDto.getOssUsername())
                .ossPassword(ossDto.getOssPassword())
                .build();
    }

    // 패스워드 Encript set
    public static OssDto withModifiedEncriptPassword(OssDto ossDto, String password) {
        return OssDto.builder()
                .ossIdx(ossDto.getOssIdx())
                .ossTypeIdx(ossDto.getOssTypeIdx())
                .ossName(ossDto.getOssName())
                .ossDesc(ossDto.getOssDesc())
                .ossUrl(ossDto.getOssUrl())
                .ossUsername(ossDto.getOssUsername())
                .ossPassword(password)
                .build();
    }
    // 패스워드 decrypt set
    public static OssDto withDetailDecryptPassword(Oss oss, String password) {
        return OssDto.builder()
                .ossIdx(oss.getOssIdx())
                .ossTypeIdx(oss.getOssType().getOssTypeIdx())
                .ossName(oss.getOssName())
                .ossDesc(oss.getOssDesc())
                .ossUrl(oss.getOssUrl())
                .ossUsername(oss.getOssUsername())
                .ossPassword(password)
                .build();
    }    // Duplicate Object set
    public static OssDto setOssAttributesDuplicate(String ossName, String ossUrl, String ossUsername) {
        return OssDto.builder()
                .ossName(ossName)
                .ossUrl(ossUrl)
                .ossUsername(ossUsername)
                .build();
    }
}
