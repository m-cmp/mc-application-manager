package kr.co.mcmp.oss.service;

import kr.co.mcmp.oss.dto.OssDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public interface OssService {
    List<OssDto> getAllOssList();
    List<OssDto> getOssList(String ossTypeName);
    Boolean isOssInfoDuplicated(OssDto ossDto);
    Long registOss(OssDto ossDto);
    Long updateOss(OssDto ossDto);
    @Transactional
    Boolean deleteOss(Long ossIdx);
    Boolean checkConnection(OssDto ossDto);
    OssDto detailOss(Long ossIdx);
}