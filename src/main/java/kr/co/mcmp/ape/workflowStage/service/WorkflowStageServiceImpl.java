package kr.co.mcmp.ape.workflowStage.service;

import kr.co.mcmp.util.JenkinsPipelineUtil;
import kr.co.mcmp.ape.workflow.repository.WorkflowStageMappingRepository;
import kr.co.mcmp.ape.workflowStage.Entity.WorkflowStage;
import kr.co.mcmp.ape.workflowStage.Entity.WorkflowStageType;
import kr.co.mcmp.ape.workflowStage.dto.WorkflowStageDto;
import kr.co.mcmp.ape.workflowStage.dto.WorkflowStageTypeDto;
import kr.co.mcmp.ape.workflowStage.repository.WorkflowStageRepository;
import kr.co.mcmp.ape.workflowStage.repository.WorkflowStageTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
@Service
public class WorkflowStageServiceImpl implements WorkflowStageService {

    private final WorkflowStageRepository workflowStageRepository;

    private final WorkflowStageTypeRepository workflowStageTypeRepository;

    private final WorkflowStageMappingRepository workflowStageMappingRepository;

    @Override
    public List<WorkflowStageDto> getWorkflowStageList() {
        try {
            List<WorkflowStageDto> workflowStageDtoList = workflowStageRepository.findAll()
                    .stream()
                    .map(WorkflowStageDto::from)
                    .collect(Collectors.toList());
            return workflowStageDtoList;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public Long registWorkflowStage(WorkflowStageDto workflowStageDto) {
        try {

            Boolean existsWorkflowType = workflowStageTypeRepository.existsByWorkflowStageTypeName(workflowStageDto.getWorkflowStageTypeName());
            if(!existsWorkflowType) {
                workflowStageTypeRepository.save(WorkflowStageTypeDto.saveWorkflowStageType(workflowStageDto.getWorkflowStageTypeName(), ""));
            }

            WorkflowStageType workflowStageTypeEntity = workflowStageTypeRepository.findByWorkflowStageTypeName(workflowStageDto.getWorkflowStageTypeName());
            WorkflowStageTypeDto workflowStageTypeDto = WorkflowStageTypeDto.from(workflowStageTypeEntity);

            WorkflowStage workflowStageEntity =  workflowStageRepository.save(WorkflowStageDto.toEntity(workflowStageDto, workflowStageTypeDto));
            WorkflowStageDto result = WorkflowStageDto.from(workflowStageEntity);

            return result.getWorkflowStageIdx();
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    public Boolean updateWorkflowStage(WorkflowStageDto workflowStageDto) {
        Boolean result = false;
        try {
            WorkflowStageType workflowStageType = workflowStageTypeRepository.findByWorkflowStageTypeIdx(workflowStageDto.getWorkflowStageTypeIdx());
            WorkflowStageTypeDto workflowStageTypeDto = WorkflowStageTypeDto.from(workflowStageType);

            workflowStageRepository.save(WorkflowStageDto.toEntity(workflowStageDto, workflowStageTypeDto));

            result = true;
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return result;
    }

    @Override
    @Transactional
    public Boolean deleteWorkflowStage(Long workflowStageIdx) {
        Boolean result = false;
        try {
            if(!workflowStageMappingRepository.existsByWorkflowStageIdx(workflowStageIdx)) {
                workflowStageRepository.deleteByWorkflowStageIdx(workflowStageIdx);
                result = true;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return result;
    }

    @Override
    public WorkflowStageDto detailWorkflowStage(Long workflowStageIdx) {
        try {
            return WorkflowStageDto.from(workflowStageRepository.findByWorkflowStageIdx(workflowStageIdx));
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public Boolean isWorkflowStageNameDuplicated(String workflowStageTypeName, String workflowStageName) {
        try {
            WorkflowStageType workflowStageType = workflowStageTypeRepository.findByWorkflowStageTypeName(workflowStageTypeName);
            return workflowStageRepository.existsByWorkflowStageTypeAndWorkflowStageName(workflowStageType, workflowStageName);
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    @Override
    public List<WorkflowStageDto> getDefaultWorkflowStage(String workflowStageTypeName) {
        try {

            Boolean existsWorkflowType = workflowStageTypeRepository.existsByWorkflowStageTypeName(workflowStageTypeName);

            if(existsWorkflowType) {
                // 1. 타입 Dto 조회
                WorkflowStageTypeDto workflowStageTypeDto =
                        WorkflowStageTypeDto.from(workflowStageTypeRepository.findByWorkflowStageTypeName(workflowStageTypeName));
                // 2. 스테이지 Dto 조회
                List<WorkflowStageDto> workflowStageDtoList =
                        workflowStageRepository.findByWorkflowStageType(WorkflowStageTypeDto.toEntity(workflowStageTypeDto))
                                .stream()
                                .map(WorkflowStageDto::from)
                                .collect(Collectors.toList());

                // 3. 없을경우 default 스크립트 만들어서 set
                if ( CollectionUtils.isEmpty(workflowStageDtoList) ) {
                    StringBuffer sb = new StringBuffer();

                    JenkinsPipelineUtil.appendLine(sb, "stage('" + workflowStageTypeName.toLowerCase().replaceAll("_", " ") + "') {", 2);
                    JenkinsPipelineUtil.appendLine(sb, "steps {", 3);
                    JenkinsPipelineUtil.appendLine(sb, "echo '>>>>>STAGE: " + workflowStageTypeName + "'", 4);
                    JenkinsPipelineUtil.appendLine(sb, "", 1);
                    JenkinsPipelineUtil.appendLine(sb, "// 스크립트를 작성해주세요.", 4);
                    JenkinsPipelineUtil.appendLine(sb, "}", 3);
                    JenkinsPipelineUtil.appendLine(sb, "}", 2);
                    JenkinsPipelineUtil.appendLine(sb, "", 1);

                    // 스테이지 Dto에 타입Idx, 스크립트만 넣어서 리스트에 넣어준다.
                    WorkflowStageDto workflowStageDto =
                            WorkflowStageDto.setWorkflowStageDefaultScript(workflowStageTypeDto.getWorkflowStageTypeIdx(), sb.toString());
                    workflowStageDtoList.add(workflowStageDto);
                }
                return workflowStageDtoList;
            }
            else {
                StringBuffer sb = new StringBuffer();

                JenkinsPipelineUtil.appendLine(sb, "stage('" + workflowStageTypeName.toLowerCase().replaceAll("_", " ") + "') {", 2);
                JenkinsPipelineUtil.appendLine(sb, "steps {", 3);
                JenkinsPipelineUtil.appendLine(sb, "echo '>>>>>STAGE: " + workflowStageTypeName + "'", 4);
                JenkinsPipelineUtil.appendLine(sb, "", 1);
                JenkinsPipelineUtil.appendLine(sb, "// 스크립트를 작성해주세요.", 4);
                JenkinsPipelineUtil.appendLine(sb, "}", 3);
                JenkinsPipelineUtil.appendLine(sb, "}", 2);
                JenkinsPipelineUtil.appendLine(sb, "", 1);

                // 스테이지 Dto에 타입Idx, 스크립트만 넣어서 리스트에 넣어준다.
                List<WorkflowStageDto> workflowStageDtoList =
                        WorkflowStageDto.setWorkflowStageDefaultScriptList(0L, sb.toString());
                return workflowStageDtoList;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }
}
