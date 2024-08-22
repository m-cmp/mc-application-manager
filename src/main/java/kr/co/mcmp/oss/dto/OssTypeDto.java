package kr.co.mcmp.oss.dto;

import kr.co.mcmp.oss.entity.OssType;
import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
public class OssTypeDto {

    private Long ossTypeIdx;
    private String ossTypeName;
    private String ossTypeDesc;

    // from : 외부 (entity -> dto)
    public static OssTypeDto from(OssType ossType) {
        return OssTypeDto.builder()
                .ossTypeIdx(ossType.getOssTypeIdx())
                .ossTypeName(ossType.getOssTypeName())
                .ossTypeDesc(ossType.getOssTypeDesc())
                .build();
    }

    // of : 내부 (dto -> dto)
    public static OssTypeDto of(OssTypeDto ossTypeDto) {
        return OssTypeDto.builder()
                .ossTypeIdx(ossTypeDto.getOssTypeIdx())
                .ossTypeName(ossTypeDto.getOssTypeName())
                .ossTypeDesc(ossTypeDto.getOssTypeDesc())
                .build();
    }

    // toEntity : Entity 변환 (dto -> entity)
    public static OssType toEntity(OssTypeDto ossTypeDto) {
        return OssType.builder()
                .ossTypeIdx(ossTypeDto.getOssTypeIdx())
                .ossTypeName(ossTypeDto.getOssTypeName())
                .ossTypeDesc(ossTypeDto.getOssTypeDesc())
                .build();
    }
}
