export interface Oss {
  ossIdx: number
  ossTypeIdx: number
  ossName: string
  ossDesc: string
  ossUsername: string
  ossPassword: string
  ossUrl: string
}
export interface OssType {
  ossTypeIdx: number
  ossTypeName: string
  ossTypeDesc: string
}

export interface Pod {
  metadata: {}
  spec: {}
}

export interface Deployment {
  metadata: {}
  spec: {}
}

export interface ConfigMap {
  metadata: {}
  data: {}
}

export interface Hpa {
  metadata:{}
  spec: {}
}

export interface Service {
  metadata:{}
  spec: {}
}

export interface Repository {
  name: string
  format: string
  type: string
  url: string
  online: boolean
  storage: {}
  docker: {}
}

export interface Component {
  id: string
  repository: string
  format: string
  group: string
  name: string
  assets: []
}

export interface SoftwareCatalog {
  id: number
  title: string
  description: string
  summary: string
  category: string
  sourceType: string
  logoUrlLarge: string
  logoUrlSmall: string
  registeredById: number
  hpaEnabled: boolean
  catalogRefs: Array<CatalogRefData>
  packageInfo?: PackageInfo
  helmChart?: HelmChart

  recommendedCpu: number
  recommendedMemory: number
  recommendedDisk: number

  minCpu: number
  minMemory: number
  minDisk: number

  cpuThreshold: number
  memoryThreshold: number

  minReplicas: number
  maxReplicas: number

  createdAt: Date
  updatedAt: Date

  isShow: boolean
}

export interface CatalogRefData {
  id: number
  catalogId: number
  refId: number
  refValue: string
  refDesc: string
  refType: string
}

export interface PackageInfo {
  id: number
  catalogId: number
  packageType: string
  packageName: string
  packageVersion: string
  repositoryUrl: string
  dockerImageId: string
  dockerPublisher: string
  dockerCreatedAt: Date
  dockerUpdatedAt: Date
  dockerShortDescription: string
  dockerSource: string
}

export interface HelmChart {
  id: number
  catalogId: number
  chartName: string
  chartVersion: string
  chartRepositoryUrl: string
  valuesFile: string
  packageId: string
  normalizedName: string
  hasValuesSchema: true
  repositoryName: string
  repositoryOfficial: true
  repositoryDisplayName: string
}

export interface ApplicationStatus {
  id: number
  type: string
  appName: string
  infraInfo: string
  status: string
  checkedAt: Date
}
export interface VmApplicationStatus {
  id: number
  catalogId: number
  applicationName: string
  deploymentType: string
  namespace: string
  mciId: string
  vmId: string
  clusterName: string
  status: string
  cpuUsage: number
  memoryUsage: number
  networkIn: number
  isPortAccessible: true
  isHealthCheck: true
  publicIp: string
  networkOut: number
  servicePort: number
  podStatus: string
  checkedAt: Date
  executedById: number
  errorLogs: [
    string
  ]
}
export interface K8sApplicationStatus {

}