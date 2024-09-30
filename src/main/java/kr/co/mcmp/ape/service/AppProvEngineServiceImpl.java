package kr.co.mcmp.ape.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.NamespaceDto;
import kr.co.mcmp.ape.dto.reqDto.JenkinsJobDto;
import kr.co.mcmp.ape.dto.resDto.ApeLogResDto;
import kr.co.mcmp.ape.service.jenkins.service.JenkinsService;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;
import kr.co.mcmp.oss.service.OssService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AppProvEngineServiceImpl implements AppProvEngineService {

    @Autowired
    private JenkinsService jenkinsService;

    @Lazy
    @Autowired
    private OssService ossService;

    private static final int MAX_LOGS = 100; // 최대 로그 갯수 제한

    @Override
    public void createJenkinsPipeline(OssTypeDto ossTypeDto, OssDto ossDto) {
        if(ossTypeDto.getOssTypeName().equalsIgnoreCase("JENKINS")){
            
            boolean isConnect = jenkinsService.isJenkinsConnect(ossDto);
            if(isConnect){
                jenkinsService.createJenkinsDefaultJobs(ossDto);
            }
        }
    }
    
    @Override
    public List<ApeLogResDto> getApeLog(String jobName) {
        OssDto jenkinsOss = getJenkinsOss();
        return fetchLogs(jenkinsOss, jobName);
    }

    private OssDto getJenkinsOss() {
        return ossService.getOssListNotDecryptPassword("JENKINS").stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Jenkins OSS 정보를 찾을 수 없습니다."));
    }

    private List<ApeLogResDto> fetchLogs(OssDto ossDto, String jobName) {
        List<ApeLogResDto> logs = new ArrayList<>();
        for (int buildNumber = 1; buildNumber <= MAX_LOGS; buildNumber++) {
            Optional<ApeLogResDto> log = fetchSingleLog(ossDto, buildNumber, jobName);
            if (log.isPresent()) {
                logs.add(log.get());
            } else {
                break; // 로그를 찾지 못하면 중단
            }
        }
        return logs;
    }

    
    private Optional<ApeLogResDto> fetchSingleLog(OssDto ossDto, int buildNumber, String jobName) {
        try {
            String logContent = jenkinsService.getJenkinsLog(
                    ossDto.getOssUrl(),
                    ossDto.getOssUsername(),
                    ossDto.getOssPassword(),
                    jobName,
                    buildNumber
            );
            return Optional.ofNullable(logContent)
                    .filter(content -> !content.isEmpty())
                    .map(content -> ApeLogResDto.of(buildNumber, content));
        } catch (Exception e) {
            // log.debug("Error fetching log for build {}: {}", buildNumber, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String getJobStatus(String jobId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getJobStatus'");
    }

    @Override
    public String triggerJenkinsJob(JenkinsJobDto jobDto) {
        OssDto jenkinsOss = getJenkinsOss();
        Map<String, List<String>> jenkinsJobParams = jobDto.convertToJenkinsParams();
        int buildNumber = jenkinsService.buildJenkinsJob(
            jenkinsOss,
            jobDto.getJobName(),
            jenkinsJobParams
        );
        log.info("Jenkins job triggered successfully. Build number: {}", buildNumber);

        return String.valueOf(buildNumber);
    }


}
