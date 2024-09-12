package kr.co.mcmp.ape.service;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;

public class AppProvEngineServiceImpl implements AppProvEngineService {

    @Override
    public void createJenkinsJob(OssTypeDto ossTypeDto, OssDto ossDto) {
        try {
            if(ossTypeDto.getOssTypeName().equalsIgnoreCase("JENKINS")){
                
            }
        } catch (Exception e) {

        }
    }
    
}
