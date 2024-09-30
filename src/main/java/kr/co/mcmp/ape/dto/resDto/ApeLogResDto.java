package kr.co.mcmp.ape.dto.resDto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ApeLogResDto {

    private int buildIdx;
    private String buildLog;

    public static ApeLogResDto of(int buildIdx, String buildLog){
        return ApeLogResDto.builder()
            .buildIdx(buildIdx)
            .buildLog(buildLog)
            .build();
    }

    public static List<ApeLogResDto> addToList(List<ApeLogResDto> list, int buildIdx, String buildLog) {
        ApeLogResDto workflowLogResDto = ApeLogResDto.builder()
            .buildIdx(buildIdx)
            .buildLog(buildLog)
            .build();

        list.add(workflowLogResDto);
        return list;
    }
}
