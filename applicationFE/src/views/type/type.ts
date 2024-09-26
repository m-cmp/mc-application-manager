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
  catalogIdx: number
  catalogTitle: string
  catalogDescription: string
  catalogSummary: string
  catalogIcon: string
  catalogCategory: string
  catalogRefData: []
}