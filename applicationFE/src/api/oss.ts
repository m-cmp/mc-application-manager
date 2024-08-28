import request from "../common/request";
import type { Oss } from "../views/type/type";


// OSS Type 목록
export const getOssTypeList = () => {
  return request.get('/ossType/list')
}

// OSS Type 필터링 목록
// 이미 생성된 OSS는 또 다시 생성할 수 없다
export const getOssTypeFilteredList = () => {
  return request.get('/ossType/filter/list')
}

// OSS 목록
export const getOssAllList = () => {
  return request.get('/oss/list')
}

// OSS 목록
export const getOssList = (ossTypeName:string) => {
  return request.get(`/oss/list/${ossTypeName}`)
}

// 중복확인
export function duplicateCheck(param: { ossName:string, ossUrl:string, ossUsername:string}) {
  return request.get(`/oss/duplicate?ossName=${param.ossName}&ossUrl=${param.ossUrl}&ossUsername=${param.ossUsername}`)
}


// 연결 확인
export function ossConnectionChecked(param: { ossUrl: string, ossUsername: string, ossPassword: string, ossTypeIdx: number }) {
  return request.post(`/oss/connection-check`, param)
}


// OSS 상세
export function getOssDetailInfo(ossIdx:number | string | string[]) {
  return request.get("/oss/" + ossIdx);
}


// OSS 등록
export function registOss(param:Oss) {
  return request.post(`/oss`, param)
}

// OSS 수정
export function updateOss(param: Oss) {
  return request.patch(`/oss/${param.ossIdx}`, param)
}

// OSS 삭제
export function deleteOss(ossIdx: number) {
  return request.delete(`/oss/${ossIdx}`)
}