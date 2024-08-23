package kr.co.mcmp.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CatalogRepository extends JpaRepository<CatalogEntity, Integer> {

    void deleteById(Integer catalogIdx);

    List<CatalogEntity> findByTitleLikeIgnoreCase(String title);

}
