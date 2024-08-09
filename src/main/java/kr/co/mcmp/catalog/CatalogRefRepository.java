package kr.co.mcmp.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatalogRefRepository extends JpaRepository<CatalogRefEntity, Integer> {

    List<CatalogRefEntity> findByCatalogId(Integer catalogId);
    //List<CatalogRefEntity> findByCatalogIdOrderByReferenceTypeAsc(Integer catalogId);

}
