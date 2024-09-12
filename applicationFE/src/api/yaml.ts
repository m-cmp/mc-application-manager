import request from "../common/request";
import type { Pod, Hpa, Deployment } from "../views/type/type";

// POD YAML
export const generateYamlPod = (param:Pod) => {
  return request.post('/yaml/pod', param)
}

// SERVICE YAML
export const generateYamlService = (param:Pod) => {
  return request.post('/yaml/service', param)
}

// HPA YAML
export const generateYamlHpa = (param:Hpa) => {
  return request.post('/yaml/hpa', param)
}

// deployment YAML
export const generateYamlDeployment = (param:Deployment) => {
  return request.post('/yaml/deployment', param)
}

// configmap YAML
export const generateYamlConfigmap = (param:Deployment) => {
  return request.post('/yaml/configmap', param)
}