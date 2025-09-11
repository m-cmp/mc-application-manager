package kr.co.mcmp.softwarecatalog.category.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngressConfigDTO {
    private String id;
    private String path;
    private Long catalogId; // SoftwareCatalog의 ID를 저장
}
