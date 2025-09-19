import request from "../common/request";
import type { SoftwareCatalog } from "@/views/type/type";

// software catalog list
export const getSoftwareCatalogList = (name:string) => {
  return request.get(`/catalog/software?name=${name}`)
}

export const getSoftwareCaltalogDetail = (catalogIdx :number | null) => {
  return request.get(`/catalog/software/${catalogIdx}`)
}

export const createCatalog = (param: object) => {
  return request.post(`/catalog/software`, param)
}

export const searchDockerhub = (keyword: string) => {
  return request.get(`/search/dockerhub/${keyword}`)
}

export const searchArtifacthubhub = (keyword: string) => {
  return request.get(`/search/artifacthub/${keyword}`)
}

// Application 설치 (VM)
export const runVmInstall = (params: {
  namespace: string,
  mciId: string,
  vmId: string,
  catalogId: number,
  servicePort: number,
  username: string,
  deploymentType: string,
}) => {
  return request.post(`/applications/vm/deploy`, params)
}

// Application Action (VM -> INSTALL, UNINSTALL, RUN, RESTART, STOP)
export const runVmAction = (params: {
  operation: string,
  applicationStatusId: number,
  reason: string
}) => {
  return request.get(`/applications/vm/action?operation=${params.operation}&applicationStatusId=${params.applicationStatusId}&reason=${params.reason}`)
}

// Application 설치 (K8S)
export const runK8SInstall = (params: {
  namespace: string,
  clusterName: string,
  catalogId: number
}) => {
  return request.post(`/applications/k8s/deploy`, params)
}

// Application Action (K8S -> INSTALL, UNINSTALL, RUN, RESTART, STOP)
export const runK8SAction = (params: any) => {
  return request.get(`/applications/k8s/action?operation=${params.operation}&applicationStatusId=${params.applicationStatusId}&reason=${params.reason}`)
}

export const vmSpecCheck = (params: {
  namespace: string,
  mciName: string,
  vmName: string,
  catalogId: number
}) => {
  return request.get(`/applications/vm/check?namespace=${params.namespace}&mciId=${params.mciName}&vmId=${params.vmName}&catalogId=${params.catalogId}`)
}

export const k8sSpecCheck = (params: {
  namespace: string,
  clusterName: string,
  catalogId: number
}) => {
  return request.get(`/applications/k8s/check?namespace=${params.namespace}&clusterName=${params.clusterName}&catalogId=${params.catalogId}`)
}

export const getBuildLogList = (jobName: string) => {
  return request.get(`/ape/log/${jobName}`)
}

export function createSoftwareCatalog(params: any) {
  return request.post(`/catalog/software`, params)
}

export function updateSoftwareCatalog(params: any) {
  return request.put(`/catalog/software/${params.id}`, params)
}

export function deleteSoftwareCatalog(catalogId: number) {
  return request.delete(`/catalog/software/${catalogId}`)
}

export function getApplicationsStatus() {
  return request.get(`/api/applications/status/groups`)
}

export function applicationAction(
  params: {
  operation: string,
  applicationStatusId: number
}) {
  return request.get(`/applications/vm/action?operation=${params.operation}&applicationStatusId=${params.applicationStatusId}`)
}


// wizard
export function getCategoryList(params: { target: string }) {
  return request.post(`/catalog/application/category`, params)
}

export function getPackageList(params: { target: string, category: string }) {
  return request.post(`/catalog/application/package`, params)
}

export function getVersionList(params: { target: string, packageName: string }) {
  const _params = {
    target: params.target,
    applicationName: params.packageName
  }
  return request.post(`/catalog/application/package/version`, _params)
}

export function getApplicationTagForDockerHub(params: { path: string }) {
  return request.get(`/search/dockerhub/tag/${params.path}`)
}

export function getApplicationTagForArtifactHub(params: { kind: string, repository: string, packageName: string }) {
  // return request.get(`/search/artifacthub/version/${params.path}`)
  return request.get(`/search/artifacthub/version/${params.kind}/${params.repository}/${params.packageName}`)
}

export function upLoadDockerHubApplication(params: any) {
  return request.post(`/catalog/docker/register`, params)
}

export function upLoadArtifactHubApplication(params: any) {
  return request.post(`/catalog/helm/register`, params)
}

// 애플리케이션 평가 제출
export function submitApplicationRating(params: {
  catalogId: number,
  rating: number,
  category: string,
  detailedComments: string,
  name: string,
  email: string,
  metadata: string
}) {
  return request.post(`/catalog/rating/overall`, params)
}

// 애플리케이션 상세 정보 조회
export function getApplicationDetail(deploymentId: number) {
  return request.get(`/api/applications/integrated/deployment/${deploymentId}`)
}