import request from "@/common/request";

// 워크플로우 목록
export const getWorkflowList = () => {
  return request.get('/workflow/list')
}

export const getOssList = () => {
  return request.get('/config/oss/list?ossCd=JENKINS')
}

// // 워크플로우 상세
// export function getWorkflowDetailInfo(workflowId:number) {
//   return request.get("/workflow/" + workflowId);
// }

// // default 스크립트
// export function getDefaultPipeline(params) {
//   return request.post(`/workflow/jenkins/pipeline/default`, params)
// }

// export function getPipelineCdList() {
//   return request.get(`/common/group/pipeline`);
// }

// // 배포 실행
// export function runWorkflowDeploy(params) {
//   return request.post(`/workflow/run`, params);
// }

// export function duplicateCheck(params) {
//   return request.get(`/workflow/name/duplicate?workflowName=${params.workflowName}`)
// }

// export function getWorkflowPipelineCdList() {
//   return request.get(`/workflow/jenkins/pipeline`)
// }

// export function postWorkflowDeploy(params) {
//   return request.post(`/workflow`, params);
// }

// export function deleteWorkflowDeploy(workflowId) {
//   return request.delete(`/workflow/${workflowId}`);
// }

// export function getWorkflowDeployDetailInfo(workflowId) {
//   return request.get(`/workflow/${workflowId}`);
// }

// export function putWorkflowDeploy(params) {
//   return request.put(`/workflow/${params.workflowId}`, params);
// }

// export function workflowHistoryList(workflowId) {
//   return request.get(`/jenkins/logs/${workflowId}`)
// }






















// export function getPipelineLog(params) {
//   return request.post('/getPipelineLog', params)
// }

// export function getPipelineLogDetail(link) {
//     return request({
//         headers:{'Content-Type': 'application/json' },
//         url: '/getPipelineLogDetail',
//         method: 'post',
//         data:link
//     })
//   }

// export function getConsoleLog(params) {
//     return request.post('/deploy/getConsoleLog', params)
// }


// export function getProfiles(params) {
//     return request.post('/projects/profiles', params)
// }

// // 수정 완료
// export function getStageList(){
//     return request.get('/common/group/stage')
// }

// export function getDeployCdList(){
//     return request.get('/common/group/deploy')
// }


// // 수정 완료
// export function getProviderList(){
//     return request.get('/common/group/provider')
// }

// export function getStageListByRemoteHostId(remoteHostId){
//     return request.get('/deploy/getStageListByRemoteHostId/'+remoteHostId)
// }

// // 수정 완료
// export function getDeployConfigCount(providerCd){
//     return request.get(`/config/k8s/count?providerCd=${providerCd}`);    
//     // return request.get(`/config/k8s/count?serviceGroupId=${serviceGroupId}`);

//     // return request.get(`/config/k8s/count?stageCd=`);
// }

// export function gitlabCloneUrlCheck(params){
//     return request.get(`/deploy/gitlab/connection/check?gitlabId=${params.gitlabId}&gitlabProjectPath=${params.gitlabProjectPath}`)
// }






