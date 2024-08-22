package kr.co.mcmp.oss.repository;

import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.oss.entity.OssType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OssTypeRepository extends JpaRepository<OssType, Long> {
    List<OssType> findByOssTypeName(String ossTypeName);
    OssType findByOssTypeIdx(Long ossTypeIdx);
    OssType save(OssTypeDto ossTypeDto);
    void deleteById(Long ossTypeIdx);
}
