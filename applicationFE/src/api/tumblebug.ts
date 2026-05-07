import request from "../common/request";

export const getNsInfo = () => {
  return request.get(`/cbtumblebug/ns`)
}

export const getMciInfo = (nsId: string) => {
  return request.get(`/cbtumblebug/ns/${nsId}/infra`)
}

export const getVmInfo = (params: {
  nsId: string,
  mciId: string
}) => {
  return request.get(`/cbtumblebug/ns/${params.nsId}/infra/${params.mciId}`)
}

export const getClusterInfo = (nsId: string) => {
  return request.get(`/cbtumblebug/ns/${nsId}/k8scluster`)
}




