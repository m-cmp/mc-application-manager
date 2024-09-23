package kr.co.mcmp.ape.workflow.service;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;

public interface AppProvEngineService {
    
    void createJenkinsPipeline(OssTypeDto ossTypeDto, OssDto ossDto);
}
