package kr.co.mcmp.ape.service;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;

public interface AppProvEngineService {
    
    void createJenkinsJob(OssTypeDto ossTypeDto, OssDto ossDto);
}
