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
  podName: string
  namespace: string
  labels: []
  image: string
  containerPort: []
  env: []
  volumeMounts: []
}

export interface Deployment {
  deployName: string
  namespace: string
  labels: []
  selector: []
  replicas: number
  image: string
  containerPort: []
  env: []
  volumeMounts: []
}

export interface Hpa {
  hpaName: string
  namespace: string
  labels: {}
  target: {}
  metric: {}
  minReplicas: number
  maxReplicas: number
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