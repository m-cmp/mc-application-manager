package kr.co.mcmp.oss.service;

import kr.co.mcmp.oss.dto.OssTypeDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public interface OssTypeService {
    List<OssTypeDto> getAllOssTypeList();
    Long registOssType(OssTypeDto ossTypeDto);
    Long updateOssType(OssTypeDto ossTypeDto);
    @Transactional
    Boolean deleteOssType(Long ossIdx);
    OssTypeDto detailOssType(Long ossIdx);
}