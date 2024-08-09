package kr.co.mcmp.tablehistory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TableHistoryService {

    @Autowired
    TableHistoryRepository tableRepo;

    public void setHistory(TableHistoryDto tableHistoryDto){
        TableHistoryEntity tblEntity = new TableHistoryEntity(tableHistoryDto);
        tableRepo.save(tblEntity);
    }

    public List<TableHistoryDto> getHistory(TableHistoryDto tableHistoryDto){

        List<TableHistoryEntity> historyEntityList = tableRepo.findByTablename(tableHistoryDto.getTablename());
        List<TableHistoryDto> tableHistoryDtoList = new ArrayList<>();

        for(TableHistoryEntity e : historyEntityList){
            TableHistoryDto d = TableHistoryDto.builder()
                    .tablename(e.getTablename())
                    .action(e.getAction())
                    .actionDate(e.getActionDate())
                    .user(e.getUser()).build();
            tableHistoryDtoList.add(d);
        }

        return tableHistoryDtoList;

    }


}
