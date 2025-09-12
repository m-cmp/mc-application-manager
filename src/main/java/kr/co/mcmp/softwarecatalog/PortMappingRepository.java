package kr.co.mcmp.softwarecatalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PortMappingRepository extends JpaRepository<PortMapping, Long> {
    
    List<PortMapping> findByCatalogId(Long catalogId);
    
    @Modifying
    @Query("delete from PortMapping p where p.catalog.id = :catalogId")
    void deleteAllByCatalogId(@Param("catalogId") Long catalogId);
    
    void deleteByCatalog(SoftwareCatalog catalog);
}
