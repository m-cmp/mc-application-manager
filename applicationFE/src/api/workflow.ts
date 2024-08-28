import request from "../common/request";
import type { Workflow } from "../views/type/type"

// 워크플로우 목록
// export const getWorkflowList = () => {
//   return request.get('/workflow/list')
// }
export const getWorkflowList = (eventlistenerYn:String) => {
  return request.get(`/eventlistener/workflowList/${eventlistenerYn}`)
}

// 중복확인
export function duplicateCheck(workflowName:string) {
  return request.get(`/workflow/name/duplicate?workflowName=${workflowName}`)
}

// default 스크립트
export function getTemplateStage(workflowName:string) {
  return request.get(`/workflow/template/${workflowName}`)
}

// 파이프라인 목록
export function getWorkflowPipelineList() {
  return request.get(`/workflow/workflowStageList`)
}

// 파이프라인 구분 목록
export function getPipelineCdList() {
  return request.get(`/workflowStageType/list`);
}

// 워크플로우 상세
// export function getWorkflowDetailInfo(workflowIdx:number | string | string[]) {
//   return request.get("/workflow/" + workflowIdx + "/N");
// }
export function getWorkflowDetailInfo(workflowIdx:number | string | string[], eventlistenerYn:String) {
  return request.get(`/eventlistener/workflowDetail/${workflowIdx}/${eventlistenerYn}`);
}

// 저장
export function registWorkflow(workflow: Workflow | any) {
  return request.post(`/workflow`, workflow);
}

// 수정
export function updateWorkflow(workflow: Workflow | any) {
  return request.patch(`/workflow/${workflow.workflowInfo.workflowIdx}`, workflow);
}


// 삭제
export function deleteWorkflow(workflowIdx: number) {
  return request.delete(`/workflow/${workflowIdx}`);
}

// 배포 실행
export function runWorkflow(params: Workflow) {
  return request.post(`/workflow/run`, params);
}

export function existEventListener(workflowIdx: number) {
  return request.get(`/workflow/existEventListener/${workflowIdx}`);
}