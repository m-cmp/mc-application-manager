package kr.co.mcmp.ape.service;

import java.util.List;

import kr.co.mcmp.ape.dto.reqDto.JenkinsJobDto;
import kr.co.mcmp.ape.dto.resDto.ApeLogResDto;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;

public interface AppProvEngineService {
    
    void createJenkinsPipeline(OssTypeDto ossTypeDto, OssDto ossDto);

    List<ApeLogResDto> getApeLog(String jobName);

    String getJobStatus(String jobId);

    String triggerJenkinsJob(JenkinsJobDto dto);

}
