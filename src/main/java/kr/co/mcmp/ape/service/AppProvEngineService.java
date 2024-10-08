package kr.co.mcmp.ape.service;

import java.util.List;

import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;
import kr.co.mcmp.ape.dto.reqDto.JenkinsJobDto;
import kr.co.mcmp.ape.dto.resDto.ApeLogResDto;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.oss.entity.Oss;

public interface AppProvEngineService {
    
    void createJenkinsPipeline(OssTypeDto ossTypeDto, OssDto ossDto);

    List<ApeLogResDto> getApeLog(String jobName);

    String triggerJenkinsJob(JenkinsJobDto dto);

    OssDto getJenkinsOss();

}
