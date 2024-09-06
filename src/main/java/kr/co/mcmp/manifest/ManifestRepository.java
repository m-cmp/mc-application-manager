package kr.co.mcmp.manifest;

import kr.co.mcmp.catalog.CatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ManifestRepository extends JpaRepository<ManifestEntity, Integer> {



}
