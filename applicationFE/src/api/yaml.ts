import request from "../common/request";
import type { Pod, Hpa, Deployment, Service, ConfigMap } from "../views/type/type";

// POD YAML
export const generateYamlPod = (param:Pod) => {
  return request.post('/yaml/pod', param)
}

// SERVICE YAML
export const generateYamlService = (param:Service) => {
  return request.post('/manifest/v1/generator/yaml/service', param)
}

// HPA YAML
export const generateYamlHpa = (param:Hpa) => {
  return request.post('/manifest/v1/generator/yaml/hpa', param)
}

// deployment YAML
export const generateYamlDeployment = (param:Deployment) => {
  return request.post('/yaml/deployment', param)
}

// configmap YAML
export const generateYamlConfigmap = (param:ConfigMap) => {
  return request.post('/manifest/v1/generator/yaml/configmap', param)
}