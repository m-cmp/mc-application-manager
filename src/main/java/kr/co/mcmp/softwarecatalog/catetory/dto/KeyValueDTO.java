package kr.co.mcmp.softwarecatalog.catetory.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeyValueDTO {
    private String key;
    private String value;
    private Boolean isUsed;
}
