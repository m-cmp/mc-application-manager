package kr.co.mcmp.tablehistory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class TableHistoryDto {

    private String tablename;

    private String action;

    private String actionDate;

    private String user;

    public List<TableHistoryDto> setLisEntityToListDto(List<TableHistoryEntity> historyEntity){
        return historyEntity.stream().map(TableHistoryEntity::toDto).collect(Collectors.toList());
    }


}
