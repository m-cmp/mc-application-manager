import { defineStore } from 'pinia'

// interface UserData {
//   accessToken: string
//   workspaceInfo: {
//     id: string
//     name: string
//     description: string
//     created_at: string
//     updated_at: string
//   }
//   projectInfo: {
//     id: string
//     ns_id: string
//     name: string
//     description: string
//     created_at: string
//     updated_at: string
//   }
//   operationId: string
// }

// TODO : 반드시 수정 필요
export const useUserStore = defineStore('user', {
  state: () => ({
    accessToken: "" as string | null, // 있는지 확인
    workspaceInfo: {
      id: "" as string | null,
      name: "" as string | null,
      description: "" as string | null,
      created_at: "" as string | null,
      updated_at: "" as string | null
    } as any,
    projectInfo: {
      id: "" as string | null,
      ns_id: "" as string | null,
      mci_id: "" as string | null,
      cluster_id: "" as string | null,
      name: "" as string | null,
      description: "" as string | null,
      created_at: "" as string | null,
      updated_at: "" as string | null
    } as any,
    operationId: "" as string | null
  }),
  actions: {
    setUser(userData:any) {
      this.accessToken =  userData.accessToken,
      this.workspaceInfo = userData.workspaceInfo,
      this.projectInfo = userData.projectInfo,
      this.operationId = userData.operationId
    },
    getNsId () {
      return this.projectInfo.ns_id
    },
    clearUser() {
      this.accessToken = null,
      this.workspaceInfo = null,
      this.projectInfo = null,
      this.operationId = null
    }
  }
})