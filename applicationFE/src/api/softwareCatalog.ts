import request from "../common/request";
import type { SoftwareCatalog } from "@/views/type/type";

// software catalog list
export const getSoftwareCaltalogList = (title:string) => {
  return request.get(`/catalog/software/?title=${title}`)
}

export const getSoftwareCaltalogDetail = (catalogIdx :number) => {
  return request.get(`/catalog/software/${catalogIdx}`)
}

export const createCatalog = (param: object) => {
  return request.post(`/catalog/software`, param)
}