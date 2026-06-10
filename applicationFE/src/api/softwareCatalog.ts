import request from "../common/request";
import type { SoftwareCatalog } from "@/views/type/type";
import axios from "axios";
import { getApiBaseUrl } from "@/common/url";

const apiBaseUrl = getApiBaseUrl(import.meta.env.VITE_API_URL).replace(/\/$/, '')
const standardPolicyAnalysisDays = [90, 30, 7]

const isMissingPolicyAnalyzeEndpoint = (responseData: any) => {
  const detail = String(responseData?.detail || '')
  return detail.includes('No static resource') && detail.includes('/policy-recommendation/analyze')
}

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
export const runAction = (params: any) => {
  return request.post(`/applications/action`, params)
}

// Application 설치 (K8S)
export const runK8SInstall = (params: {
  namespace: string,
  clusterName: string,
  catalogId: number,
  servicePort?: number,
  username?: string,
  deploymentType?: string,
  hpaEnabled?: boolean,
  minReplicas?: number,
  maxReplicas?: number,
  cpuThreshold?: number,
  memoryThreshold?: number,
  resourceType?: string,
  ingressEnabled?: boolean,
  ingressHost?: string,
  ingressPath?: string,
  ingressClass?: string,
  ingressTlsEnabled?: boolean,
  ingressTlsSecret?: string,
  additionalConfig?: Record<string, any>
}) => {
  return request.post(`/applications/k8s/deploy`, params)
}

export const objectStorageSmokeCheck = (params: {
  namespace: string,
  clusterName: string,
  catalogId: number,
  objectStorage: Record<string, any>
}) => {
  return request.post(`/applications/k8s/object-storage/smoke-check`, params)
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

export function getCatalogDeploymentStatus(catalogId: number) {
  return request.get(`/api/applications/integrated/catalog/${catalogId}`)
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

export function analyzeOperationProfile(deploymentId: number, days = 14) {
  return request.post(`/api/applications/${deploymentId}/operation-profile/analyze?days=${days}`)
}

export async function analyzePolicyRecommendation(deploymentId: number) {
  try {
    const response = await axios.post(`${apiBaseUrl}/api/applications/${deploymentId}/policy-recommendation/analyze`)
    const responseData = response.data

    if (responseData?.code === 200) {
      return responseData
    }

    if (isMissingPolicyAnalyzeEndpoint(responseData)) {
      return analyzePolicyRecommendationWithLegacyEndpoints(deploymentId)
    }

    return Promise.reject(new Error(responseData?.detail || responseData?.message || 'Failed to analyze policy recommendation'))
  } catch (error: any) {
    if (error?.response?.status === 404) {
      return analyzePolicyRecommendationWithLegacyEndpoints(deploymentId)
    }
    return Promise.reject(error)
  }
}

async function analyzePolicyRecommendationWithLegacyEndpoints(deploymentId: number) {
  const results = []
  for (const days of standardPolicyAnalysisDays) {
    const result = await analyzeOperationProfile(deploymentId, days)
    results.push(result.data)
  }

  return {
    code: 200,
    data: results,
    detail: null,
    message: 'OK'
  }
}

export function getOperationProfile(deploymentId: number, days?: number) {
  const query = days ? `?days=${days}` : ''
  return request.get(`/api/applications/${deploymentId}/operation-profile${query}`)
}

export function getPolicyRecommendation(deploymentId: number) {
  return request.get(`/api/applications/${deploymentId}/policy-recommendation`)
}

export function savePolicyRecommendationDecision(
  recommendationId: number,
  params: {
    status: string,
    decidedBy?: string,
    decisionReason?: string
  }
) {
  return request.put(`/api/applications/policy-recommendations/${recommendationId}/decision`, params)
}

export function getReasonList(operation: string) {
  return request.get(`/catalog/selectbox/options?type=${operation}`)
}
