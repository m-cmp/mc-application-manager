package kr.co.mcmp.ape.cbtumblebug.dto;

import java.util.List;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "MCI Response")
public class MciResponse {

    @ApiModelProperty(value = "List of MCIs")
    private List<MciDto> mci;
}
