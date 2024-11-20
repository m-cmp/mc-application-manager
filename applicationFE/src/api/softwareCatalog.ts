import request from "../common/request";
import type { SoftwareCatalog } from "@/views/type/type";

// software catalog list
export const getSoftwareCatalogList = (title:string) => {
  return request.get(`/catalog/software/?title=${title}`)
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

// install API 변경됨
// export const runVmInstall = (params: {
//   namespace: string,
//   mciName: string,
//   vmName: string,
//   applications: string[]
// }) => {
//   return request.post(`/ape/vm/install`, params)
// }
export const runVmInstall = (params: {
  namespace: string,
  mciId: string,
  vmId: string,
  catalogId: number,
  servicePort: number
}) => {
  // 추후 POST 방식으로 변경 필요
  // return request.post(`/applications/vm/deploy`, params)
  return request.get(`/applications/vm/deploy?namespace=${params.namespace}&mciId=${params.mciId}&vmId=${params.vmId}&catalogId=${params.catalogId}&servicePort=${params.servicePort}`, )
}

export const runVmUninstall = (params: {
  namespace: string,
  mciName: string,
  vmName: string,
  applications: string[]
}) => {
  return request.post(`/ape/vm/uninstall`, params)
}

// install API 변경됨
// export const runK8SInstall = (params: {
//   namespace: string,
//   clusterName: string,
//   helmCharts: string[]
// }) => {
//   return request.post(`/ape/helm/install`, params)
// }
export const runK8SInstall = (params: {
  namespace: string,
  clusterName: string,
  helmCharts: string[]
}) => {
  return request.post(`/applications/k8s/deploy`, params)
}

export const runK8SUninstall = (params: {
  namespace: string,
  clusterName: string,
  helmCharts: string[]
}) => {
  return request.post(`/ape/helm/uninstall`, params)
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
  return request.put(`/catalog/software`, params)
}

export function getVmApplicationsStatus() {
  return request.get(`/applications/vm/groups`)
}

export function getK8sApplicationsStatus() {
  return request.get(`/applications/k8s/groups`)
}

export function applicationAction(
  params: {
  operation: string,
  applicationStatusId: number
}) {
  return request.get(`/applications/vm/action?operation=${params.operation}&applicationStatusId=${params.applicationStatusId}`)
}