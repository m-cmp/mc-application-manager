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

// Application 설치 (VM)
export const runVmInstall = (params: {
  namespace: string,
  mciId: string,
  vmId: string,
  catalogId: number,
  servicePort: number
}) => {
  return request.get(`/applications/vm/deploy?namespace=${params.namespace}&mciId=${params.mciId}&vmId=${params.vmId}&catalogId=${params.catalogId}&servicePort=${params.servicePort}`, )
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
  return request.get(`/applications/k8s/deploy?namespace=${params.namespace}&clusterName=${params.clusterName}&catalogId=${params.catalogId}`)
}

// Application Action (K8S -> INSTALL, UNINSTALL, RUN, RESTART, STOP)
export const runK8SAction = (params: {
  operation: string,
  applicationStatusId: number,
  reason: string
}) => {
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
  return request.put(`/catalog/software`, params)
}

export function getApplicationsStatus() {
  // return request.get(`/applications/vm/groups`)
  return request.get(`/applications/groups`)
}

export function applicationAction(
  params: {
  operation: string,
  applicationStatusId: number
}) {
  return request.get(`/applications/vm/action?operation=${params.operation}&applicationStatusId=${params.applicationStatusId}`)
}