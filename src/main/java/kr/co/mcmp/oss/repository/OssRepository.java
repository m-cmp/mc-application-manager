package kr.co.mcmp.oss.repository;

import kr.co.mcmp.oss.entity.Oss;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OssRepository extends JpaRepository<Oss, Long> {
    List<Oss> findAll();
    List<Oss> findByOssName(String ossName);
    @Query("SELECT o FROM Oss o WHERE o.ossType.ossTypeIdx IN :ossTypeIdxs")
    List<Oss> findByOssTypeIdxIn(@Param("ossTypeIdxs") List<Long> ossTypeIdxs);
    Boolean existsByOssNameAndOssUrlAndOssUsername(String ossName, String ossUrl, String ossUsername);
    Oss save(Oss oss);
    Oss findByOssType_OssTypeName(String ossTypeName);
    Oss findByOssIdx(Long ossIdx);
    void deleteByOssIdx(Long ossIdx);
}
