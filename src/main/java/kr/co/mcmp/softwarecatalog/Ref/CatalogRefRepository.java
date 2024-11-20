package kr.co.mcmp.softwarecatalog.Ref;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatalogRefRepository extends JpaRepository<CatalogRefEntity, Long> {

    List<CatalogRefEntity> findByCatalogId(Long catalogId);
    //List<CatalogRefEntity> findByCatalogIdOrderByReferenceTypeAsc(Integer catalogId);

    void deleteAllByCatalogId(Long catalogIdx);

}
