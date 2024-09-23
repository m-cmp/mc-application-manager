package kr.co.mcmp.ape.workflow.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import kr.co.mcmp.ape.workflow.Entity.Workflow;
import kr.co.mcmp.ape.workflow.dto.entityMappingDto.WorkflowDto;
import kr.co.mcmp.ape.workflow.dto.entityMappingDto.WorkflowParamDto;
import kr.co.mcmp.ape.workflow.dto.entityMappingDto.WorkflowStageMappingDto;
import kr.co.mcmp.ape.workflow.dto.resDto.WorkflowListResDto;
import kr.co.mcmp.ape.workflow.repository.WorkflowHistoryRepository;
import kr.co.mcmp.ape.workflow.repository.WorkflowParamRepository;
import kr.co.mcmp.ape.workflow.repository.WorkflowRepository;
import kr.co.mcmp.ape.workflow.repository.WorkflowStageMappingRepository;
import kr.co.mcmp.ape.workflow.service.jenkins.JenkinsPipelineGeneratorService;
import kr.co.mcmp.ape.workflow.service.jenkins.service.JenkinsService;
import kr.co.mcmp.ape.workflowStage.repository.WorkflowStageRepository;
import kr.co.mcmp.ape.workflowStage.repository.WorkflowStageTypeRepository;
import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.dto.OssTypeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Service
@Slf4j
public class AppProvEngineServiceImpl implements AppProvEngineService {


    
    private final WorkflowRepository workflowRepository;

    private final WorkflowParamRepository workflowParamRepository;

    private final WorkflowStageMappingRepository workflowStageMappingRepository;

    private final WorkflowHistoryRepository workflowHistoryRepository;

    private final WorkflowStageTypeRepository workflowStageTypeRepository;

    private final WorkflowStageRepository workflowStageRepository;

    private final JenkinsService jenkinsService;

    private final JenkinsPipelineGeneratorService pipelineService;

    private static final String PIPE_LINE_NAME = "ApplicationProvisioningPipeline";

    // public List<WorkflowListResDto> getWorkflowList() {

    //     List<WorkflowDto> workflowList = workflowRepository.findAll()
    //         .stream()
    //         .map(WorkflowDto::from)
    //         .collect(Collectors.toList());

    //     List<WorkflowListResDto> list = new ArrayList<>();
    //     workflowList.forEach((workflow)-> {

    //         Workflow workflowEntity = workflowRepository.findByWorkflowIdx(workflow.getWorkflowIdx());
    //         WorkflowDto workflowDto = WorkflowDto.from(workflowEntity);

    //         List<WorkflowParamDto> paramList =
    //                 workflowParamRepository.findByWorkflow_WorkflowIdx(workflow.getWorkflowIdx())
    //                                         .stream()
    //                                         .map(WorkflowParamDto::from)
    //                                         .collect(Collectors.toList());

    //         List<WorkflowStageMappingDto> stageList =
    //                 workflowStageMappingRepository.findByWorkflow_WorkflowIdx(workflow.getWorkflowIdx())
    //                                         .stream()
    //                                         .map(WorkflowStageMappingDto::from)
    //                                         .collect(Collectors.toList());

    //         WorkflowListResDto workflowListData = WorkflowListResDto.of(workflowDto, paramList, stageList);

    //         list.add(workflowListData);
    //     });
    //     return list;
    // }

    @Override
    public void createJenkinsPipeline(OssTypeDto ossTypeDto, OssDto ossDto) {
        if(ossTypeDto.getOssTypeName().equalsIgnoreCase("JENKINS")){
            boolean isConnect = jenkinsService.isJenkinsConnect(ossDto);
            boolean isExistJobName = jenkinsService.isExistJobName(ossDto, PIPE_LINE_NAME);
            if(isConnect && !isExistJobName){
                jenkinsService.createJenkinsPipeline(ossDto, PIPE_LINE_NAME);
            }
        }
    }
    
}
