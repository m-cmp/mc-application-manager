package kr.co.mcmp.tablehistory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TableHistoryRepository extends JpaRepository<TableHistoryEntity, Integer> {

    List<TableHistoryEntity> findByTablename(String tablename);

}
