package kr.co.mcmp.ape.workflowStage.service;

import kr.co.mcmp.ape.workflowStage.Entity.WorkflowStageType;
import kr.co.mcmp.ape.workflowStage.dto.WorkflowStageTypeDto;
import kr.co.mcmp.ape.workflowStage.repository.WorkflowStageRepository;
import kr.co.mcmp.ape.workflowStage.repository.WorkflowStageTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
@Service
public class WorkflowStageTypeServiceImpl implements WorkflowStageTypeService {

    private final WorkflowStageTypeRepository workflowStageTypeRepository;

    private final WorkflowStageRepository workflowStageRepository;

    /**
     * Workflow Stage Type 생성
     * @return
     */
    @Override
    public List<WorkflowStageTypeDto> getWorkflowStageTypeList() {
        try {
            List<WorkflowStageTypeDto> workflowStageTypeDtoList =
                    workflowStageTypeRepository.findAll()
                            .stream()
                            .map(WorkflowStageTypeDto::from)
                            .collect(Collectors.toList());
            return workflowStageTypeDtoList;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * Workflow Stage Type 등록
     * @param workflowStageTypeDto
     * @return
     */
    @Override
    public Long registWorkflowStage(WorkflowStageTypeDto workflowStageTypeDto) {
        try {
            WorkflowStageType workflowStageType = workflowStageTypeRepository.save(WorkflowStageTypeDto.toEntity(workflowStageTypeDto));
            WorkflowStageTypeDto result = WorkflowStageTypeDto.from(workflowStageType);

            return result.getWorkflowStageTypeIdx();
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * Workflow Stage Type 수정
     * @param workflowStageTypeDto
     * @return
     */
    @Override
    public Boolean updateWorkflowStageType(WorkflowStageTypeDto workflowStageTypeDto) {
        Boolean result = false;
        try {
            workflowStageTypeRepository.save(WorkflowStageTypeDto.toEntity(workflowStageTypeDto));

            result = true;
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return result;
    }

    /**
     * Workflow Stage Type 삭제
     * @param workflowStageTypeIdx
     * @return
     */
    @Override
    @Transactional
    public Boolean deleteWorkflowStageType(Long workflowStageTypeIdx) {
        Boolean result = false;
        try {
            WorkflowStageType workflowStageType = workflowStageTypeRepository.findByWorkflowStageTypeIdx(workflowStageTypeIdx);

            if(!workflowStageRepository.existsByWorkflowStageType(workflowStageType)) {
                workflowStageTypeRepository.deleteById(workflowStageTypeIdx);
                result = true;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return result;
    }

    /**
     * Workflow Stage Type 상세
     * @param workflowStageTypeIdx
     * @return
     */
    @Override
    public WorkflowStageTypeDto detailWorkflowStageType(Long workflowStageTypeIdx) {
        try {
            WorkflowStageType workflowStageEntity = workflowStageTypeRepository.findByWorkflowStageTypeIdx(workflowStageTypeIdx);

            return WorkflowStageTypeDto.from(workflowStageEntity);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }
}
