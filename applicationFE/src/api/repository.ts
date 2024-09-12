import request from "../common/request";
import type { Repository } from "@/views/type/type";

// Repository 목록
export const getRepositoryList = (module:string) => {
  return request.get(`/oss/v1/repositories/${module}/list`)
}

// Repository 삭제
export function deleteRepository(module: string, name: string) {
  return request.delete(`/oss/v1/repositories/${module}/delete/${name}`)
}

// Repository 등록
export function registRepository(module: string, param: Repository) {
  return request.post(`/oss/v1/repositories/${module}/create`, param)
}

// Repository 상세정보 조회
export const getRepositoryDetailInfo = (module:string, name: string) => {
  return request.get(`/oss/v1/repositories/${module}/detail/${name}`)
}

// Repository 수정
export const updateRepository = (module:string, param: Repository) => {
  return request.put(`/oss/v1/repositories/${module}/update`, param)
}

// Repository 삭제
export function deleteComponent(module: string, id: string) {
  return request.delete(`/oss/v1/components/${module}/delete/${id}`)
}

// 컴포넌트 목록
export const getComponentList = (module:string, name: string) => {
  return request.get(`/oss/v1/components/${module}/list/${name}`)
}


// 컴포넌트 파일 upload
export const uploadComponent = (module:string, name: string, param: object) => {
  return request.post(`/oss/v1/components/${module}/create/${name}`, param)
}