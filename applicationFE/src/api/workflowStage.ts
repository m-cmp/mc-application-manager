import request from "../common/request";
import type { WorkflowStage } from "../views/type/type";


// Workflow Stage Type 목록
export const getWorkflowStageTypeList = () => {
  return request.get('/workflowStageType/list')
}

// Workflow Stage 목록
export const getWorkflowStageList = () => {
  return request.get('/workflowStage/list')
}

// Workflow Stage 상세
export function getWorkflowStageDetailInfo(workflowStageIdx:number) {
  return request.get("/workflowStage/" + workflowStageIdx);
}

// 중복확인
export function duplicateCheck(param: { workflowStageName:string, workflowStageTypeName:string}) {
  return request.get(`/workflowStage/duplicate?workflowStageName=${param.workflowStageName}&workflowStageTypeName=${param.workflowStageTypeName}`)
}

// Workflow Stage 등록
export function registWorkflowStage(param: WorkflowStage) {
  return request.post(`/workflowStage`, param)
}

// Workflow Stage 수정
export function updateWorkflowStage(param: WorkflowStage) {
  return request.patch(`/workflowStage/${param.id}`, param)
}

// Workflow Stage 삭제
export function deleteWorkflowStage(workflowStageIdx: number) {
  return request.delete(`/workflowStage/${workflowStageIdx}`)
}

// Workflow Stage DefaultScript 조회
export function getWorkflowStageDefaultScript(workflowStageTypeName: string) {
  return request.get(`/workflowStage/default/script/${workflowStageTypeName}`)
}













