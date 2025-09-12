package kr.co.mcmp.softwarecatalog.Ref;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatalogRefRepository extends JpaRepository<CatalogRefEntity, Long> {

    List<CatalogRefEntity> findByCatalogId(Long catalogId);
    //List<CatalogRefEntity> findByCatalogIdOrderByReferenceTypeAsc(Integer catalogId);

    @Modifying
    @Query("delete from CatalogRefEntity r where r.catalog.id = :catalogId")
    void deleteAllByCatalogId(@Param("catalogId") Long catalogId);
}
