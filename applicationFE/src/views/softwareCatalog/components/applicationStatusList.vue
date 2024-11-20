<template>
  <div>
    <div class="d-flex justify-content-between">
      <h2>Apps Status</h2>
      <div>
        <span class="me-1">
          {{ refreshTime }}
        </span>
        <IconRefresh class="cursor-pointer" @click="_setApplicationsStatusList"/>
      </div>
    </div>
    <div class="card card-flush w-100">
      <Tabulator 
        :columns="columns" 
        :table-data="applicationsStatusList">
      </Tabulator>
    </div>
  </div>
</template>
<script setup lang="ts">
import Tabulator from '@/components/Table/Tabulator.vue'
import { onMounted } from 'vue';
import { ref } from 'vue';
import type { ApplicationStatus, VmApplicationStatus, K8sApplicationStatus } from '@/views/type/type'
import type { ColumnDefinition } from 'tabulator-tables';
import { useToast } from 'vue-toastification';
import { getVmApplicationsStatus, getK8sApplicationsStatus, applicationAction } from '@/api/softwareCatalog';
import { IconRefresh } from '@tabler/icons-vue'

const toast = useToast()
/**
 * @Title applicationsStatusList / columns
 * @Desc
 *    applicationsStatusList : ApplicationStatus 목록 저장
 *    columns : 목록의 컬럼 저장
 */
const applicationsStatusList = ref([] as Array<ApplicationStatus | any>)
const vmApplicationsStatusList = ref([] as Array<VmApplicationStatus | any>)
const k8sApplicationsStatusList = ref([] as Array<K8sApplicationStatus | any>)

const columns = ref([] as Array<ColumnDefinition>)
const refreshTime = ref("" as string)

/**
 * @Title Life Cycle
 * @Desc 컬럼 set Callback 함수 호출 / ApplicationStatusList Callback 함수 호출
 */
onMounted(async () => {
  setColumns()
  await _setApplicationsStatusList()
})

/**_getApplicationsStatusLis
 * @Title _setApplicationsStatusList
 * @Desc ApplicationStatus List Callback 함수 / ApplicationStatus List api 호출
 */

const now = new Date();
const options = { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false } as any;

const _setApplicationsStatusList = async () => {
  try {
    initData()

    await setApplicationsStatusListCallback()

    if (vmApplicationsStatusList.value.length > 0) {
      const vmAppStatusList = [...vmApplicationsStatusList.value]

      vmAppStatusList.forEach((vmAppStatus: VmApplicationStatus) => {
        const mappingApplicationStatus = {
          id: vmAppStatus.id,
          type: vmAppStatus.deploymentType,
          appName: vmAppStatus.applicationName,
          infraInfo: vmAppStatus.vmId,
          status: vmAppStatus.status,
          checkedAt: vmAppStatus.checkedAt,
        }
        applicationsStatusList.value.push(mappingApplicationStatus)
      })
    }
    if (k8sApplicationsStatusList.value.length > 0) {
      const k8sAppStatusList = [...k8sApplicationsStatusList.value]

      k8sAppStatusList.forEach((k8sAppStatus: K8sApplicationStatus) => {
        const mappingApplicationStatus = {
          // type: k8sAppStatus.deploymentType,
          // appName: k8sAppStatus.applicationName,
          // infraInfo: k8sAppStatus.vmId,
          // status: k8sAppStatus.status,
          // checkedAt: k8sAppStatus.checkedAt,
        }

        applicationsStatusList.value.push(mappingApplicationStatus)
      })
    }

  } catch (error) {
    console.log(error)
    toast.error('Unable to retrieve data')
  }
}

const initData = () => {
  applicationsStatusList.value = []
  vmApplicationsStatusList.value = []
  k8sApplicationsStatusList.value = []

  refreshTime.value = now.toLocaleDateString('ko-KR', options)
}

const setApplicationsStatusListCallback = async() => {
  await _getVmApplicationStatusList()
  // await _getK8sApplicationStatusList()
}

const _getVmApplicationStatusList = async () => {

  const { data } = await getVmApplicationsStatus()

  if(data)
    vmApplicationsStatusList.value = data
  else
    vmApplicationsStatusList.value = []
}

const _getK8sApplicationStatusList = async () => {

  const { data } = await getK8sApplicationsStatus()

  if(data)
    k8sApplicationsStatusList.value = data
  else 
    k8sApplicationsStatusList.value = []
}


/**
 * @Title selectInfraIdx / selectInfraName / setColumns
 * @Desc
 *    selectInfraIdx : Action을 위한 선택된 row의 infraIdx저장
 *    selectInfraName : 삭제를 위한 선택된 row의 infraName저장
 *    setColumns : 컬럼 set Callback 함수
 */
const setColumns = () => {
  columns.value = [
    {
      title: "Type",
      field: "type",
      width: '10%'
    },
    {
      title: "Application",
      field: "appName",
      width: '20%'
    },
    {
      title: "Infra",
      field: "infraInfo",
      width: '15%',
    },
    {
      title: "Status",
      // field: "status",
      width: '15%',
      formatter: statusFormatter,
    },
    
    {
      title: "CheckedAt",
      field: "checkedAt",
      width: '20%'
    },
    {
      title: "Action",
      width: '20%',
      formatter: actionButtonFormatter,
      cellClick: async function (e, cell) {
        const target = e.target as HTMLElement;
        const btnFlag = target?.getAttribute('id')
        const applicationStatusId = cell.getRow().getData().id

        if (btnFlag === 'restart-btn') {
          await _applicationAction('RESTART', applicationStatusId)
        }
        else if (btnFlag === 'stop-btn') {
          await _applicationAction('STOP', applicationStatusId)
        }
        else if (btnFlag === 'uninstall-btn') {
          await _applicationAction('UNINSTALL', applicationStatusId)
        }
      }
    }
  ]
}

const _applicationAction = async (operation: string, applicationStatusId: number) => {
  
  const result = confirm(`Are you sure you want to take ${operation}?`)

  if (result) {
    const params = {
      operation: operation,
      applicationStatusId: applicationStatusId
    }
    await applicationAction(params)
  }
}


/**
 * @Title statusFormatter
 * @Desc Status Formatter
 */
const statusFormatter = (cell: any) => {
  const status = cell.getRow().getData().status
  if (status === 'RUNNING') {
    return `
      <div>
        <span class="status status-green">  
          <span class="status-dot"></span>
            ${status}
        </span>
      </div>`
  }
  else if (status === 'RESTART' ) {
  return `
    <div>
      <span class="status status-primary">  
        <span class="status-dot"></span>
          ${status}
      </span>
    </div>`
  }
  else if (status === 'NOT_FOUND' ) {
  return `
    <div>
      <span class="status status-yellow">  
        <span class="status-dot"></span>
          ${status}
      </span>
    </div>`
  }
  else {
    return `
  <div>
    <span class="status status-red">  
      <span class="status-dot"></span>
        ${status}
    </span>
  </div>`
  }
}

/**
 * @Title actionButtonFormatter
 * @Desc 수정 / 삭제 버튼 Formatter
 */
const actionButtonFormatter = () => {
  return `
  <div>
    <button
      class='btn btn-ghost-primary d-none d-sm-inline-block'
      id='restart-btn'
      data-bs-toggle='modal'
      data-bs-target='#restartApplication'>
      Restart
    </button>
    <button
      class='btn btn-ghost-warning d-none d-sm-inline-block'
      id='stop-btn'
      data-bs-toggle='modal'
      data-bs-target='#stopApplication'>
      Stop
    </button>
    <button
      class='btn btn-ghost-danger d-none d-sm-inline-block'
      id='uninstall-btn'
      data-bs-toggle='modal'
      data-bs-target='#uninstallApplication'>
      Uninstall
    </button>
  </div>`;
}


</script>