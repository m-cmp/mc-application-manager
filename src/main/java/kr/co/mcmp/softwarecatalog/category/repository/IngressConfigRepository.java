package kr.co.mcmp.softwarecatalog.category.repository;

import kr.co.mcmp.softwarecatalog.category.entity.IngressConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngressConfigRepository extends JpaRepository<IngressConfig, Long> {
    IngressConfig findByCatalogId(Long catalogId);

    void deleteAllByCatalogId(Long catalogId);
}
