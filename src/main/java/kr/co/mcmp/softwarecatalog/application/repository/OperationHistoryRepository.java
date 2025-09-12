package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.application.model.OperationHistory;

@Repository
public interface OperationHistoryRepository extends JpaRepository<OperationHistory, Long>{
    
    List<OperationHistory> findByApplicationStatusId(Long applicationStatusId);
}
