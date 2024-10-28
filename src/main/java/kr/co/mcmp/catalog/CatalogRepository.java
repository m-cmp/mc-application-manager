package kr.co.mcmp.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogRepository extends JpaRepository<CatalogEntity, Integer> {

    void deleteById(Integer catalogIdx);

    List<CatalogEntity> findByTitleLikeIgnoreCase(String title);

    // Optional<CatalogEntity> findByTitleAndVersion(String title, String version);

    // List<CatalogEntity> findByTitleContainingIgnoreCaseAndVersion(String title, String version);
    

}
