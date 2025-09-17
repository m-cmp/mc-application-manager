package kr.co.mcmp.softwarecatalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectBoxOptionDTO {
    private String value;
    private String label;
    private String description;
}
