<template>
  <div class="card w-100">
    <Tabulator
      :columns="columns"
      :table-data="applicationsStatusList">
    </Tabulator>
  </div>
  <ApplicationActionConfirm 
    ref="applicationActionConfirmModalRef"
    :title="actionModalTitle" 
    :applicationStatusId="applicationStatusId" 
    :type="deploymentType"
    :applicationName="applicationName"
    @getApplicationsStatusList="_getApplicationsStatusList"
    />
  <ApplicationRatingModal 
    :catalogId="catalogId"
    :applicationName="applicationName"
    @ratingSubmitted="_getApplicationsStatusList"
    />
  <ApplicationDetailModal 
    ref="applicationDetailModalRef"
    :modal-id="applicationStatusDetailModalId"
    :deploymentId="selectedDeploymentId"
    />
</template>
<script setup lang="ts">
import Tabulator from '@/components/Table/Tabulator.vue'
import { ref, onBeforeUnmount, onMounted, watch } from 'vue';
import type { ApplicationStatus } from '@/views/type/type'
import type { ColumnDefinition } from 'tabulator-tables';
import { useToast } from 'vue-toastification';
import { getApplicationsStatus } from '@/api/softwareCatalog';
import ApplicationActionConfirm from './applicationActionConfirm.vue';
import ApplicationRatingModal from './applicationRatingModal.vue';
import ApplicationDetailModal from './applicationDetailModal.vue';
import {
  getApplicationStatusBadgeClass,
  getApplicationStatusLabel,
  isApplicationActionDisabledStatus
} from '../applicationStatusDisplay'

const toast = useToast()

interface Props {
  nsId?: string
}

const props = withDefaults(defineProps<Props>(), {
  nsId: ''
})
const emit = defineEmits<{
  (e: 'refresh-time-updated', value: string): void
}>()
const isEmbedded = window.self !== window.top
let mounted = false
let statusLoadSequence = 0
/**
 * @Title applicationsStatusList / columns
 * @Desc
 *    applicationsStatusList : ApplicationStatus 목록 저장
 *    columns : 목록의 컬럼 저장
 */
const applicationsStatusList = ref([] as Array<ApplicationStatus | any>)
const columns = ref([] as Array<ColumnDefinition>)
const refreshTime = ref("" as string)

const actionModalTitle = ref('' as string)
const applicationStatusId = ref(0 as number)
const deploymentType = ref('' as string)
const applicationName = ref('' as string)
const catalogId = ref(0 as number)
const showRatingModal = ref(false)
const selectedDeploymentId = ref(0 as number)
const applicationStatusDetailModalId = 'application-status-detail-modal'
const applicationDetailModalRef = ref()
const applicationActionConfirmModalRef = ref()
/**
 * @Title Life Cycle
 * @Desc 컬럼 set Callback 함수 호출 / ApplicationStatusList Callback 함수 호출
 */
onMounted(async () => {
  mounted = true
  setColumns()
  await _getApplicationsStatusList()
})

onBeforeUnmount(() => {
  statusLoadSequence += 1
})

watch(() => props.nsId, async (newNamespace, previousNamespace) => {
  if (newNamespace === previousNamespace) return

  statusLoadSequence += 1
  applicationsStatusList.value = []
  applicationStatusId.value = 0
  selectedDeploymentId.value = 0
  deploymentType.value = ''
  applicationName.value = ''
  catalogId.value = 0
  applicationDetailModalRef.value?.resetData()
  if (mounted) {
    await _getApplicationsStatusList()
  }
})

/**_getApplicationsStatusList
 * @Title _getApplicationsStatusList
 * @Desc ApplicationStatus List Callback 함수 / ApplicationStatus List api 호출
 */
const _getApplicationsStatusList = async () => {
  const loadSequence = ++statusLoadSequence
  const namespace = String(props.nsId || '').trim()
  try {
    initData()

    // In Web Console, wait for the selected Project context instead of
    // briefly requesting an unscoped list while the iframe is initializing.
    if (isEmbedded && !namespace) {
      applicationsStatusList.value = []
      return
    }

    const { data } = await getApplicationsStatus(namespace || undefined)
    if (loadSequence !== statusLoadSequence || namespace !== String(props.nsId || '').trim()) {
      return
    }

    if (data) {
      applicationsStatusList.value = data
    }

    else {
      applicationsStatusList.value = []
    }

  } catch (error) {
    if (loadSequence !== statusLoadSequence) return
    toast.error('Unable to retrieve data')
  }
}

const initData = () => {
  const now = new Date();
  const options = { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false } as any;

  refreshTime.value = now.toLocaleDateString('ko-KR', options)
  emit('refresh-time-updated', refreshTime.value)
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
    // {
    //   title: "Type",
    //   field: "deploymentType",
    //   width: '15%'
    // },
    {
      title: "Infra",
      width: 230,
      minWidth: 180,
      formatter: infraFormatter,
      cellClick: function (e, cell) {
        openDetailModal(cell)
      }
    },
    {
      title: "Application",
      field: "applicationName",
      width: 180,
      minWidth: 150,
      cellClick: function (e, cell) {
        openDetailModal(cell)
      },
      formatter: function(cell) {
        return `<div style="cursor: pointer; color:">${cell.getValue()}</div>`
      }
    },
    {
      title: "Status",
      width: 210,
      minWidth: 190,
      formatter: statusFormatter,
      cellClick: function (e, cell) {
        openDetailModal(cell)
      }
    },
    
    {
      title: "CheckedAt",
      field: "checkedAt",
      width: 210,
      minWidth: 190,
      cellClick: function (e, cell) {
        openDetailModal(cell)
      },
      formatter: function(cell) {
        return `<div style="cursor: pointer;">${cell.getValue()}</div>`
      }
    },
    {
      title: "Action",
      width: 320,
      minWidth: 300,
      headerSort: false,
      formatter: actionButtonFormatter,
      cellClick: async function (e, cell) {
        const target = e.target as HTMLElement;
        const btnFlag = target?.getAttribute('id')
        const status = cell.getRow().getData().status
        if (isActionDisabledStatus(status)) {
          return
        }
        const applicationStatusId = cell.getRow().getData().id
        const deploymentType = cell.getRow().getData().deploymentType
        const applicationName = cell.getRow().getData().applicationName

        const params = {
          operation: '' as string,
          applicationStatusId: applicationStatusId as number,
          deploymentType: deploymentType as string,
          applicationName: applicationName as string
        }
        if (btnFlag === 'start-btn') {
          params.operation = 'START'
          await _applicationAction(params)
        }
        else if (btnFlag === 'restart-btn') {
          params.operation = 'RESTART'
          await _applicationAction(params)
        }
        else if (btnFlag === 'stop-btn') {
          params.operation = 'STOP'
          await _applicationAction(params)
        }
        else if (btnFlag === 'uninstall-btn') {
          params.operation = 'UNINSTALL'
          await _applicationAction(params)
        }
        else if (btnFlag === 'rating-btn') {
          params.operation = 'RATING'
          catalogId.value = cell.getRow().getData().catalogId || 1; // catalogId 설정
          await _applicationAction(params)
        }
      }
    }
  ]
}

const _applicationAction = async (params: {
  operation: string,
  applicationStatusId: number,
  deploymentType: string,
  applicationName: string
}) => {
  actionModalTitle.value = params.operation
  applicationStatusId.value = params.applicationStatusId
  deploymentType.value = params.deploymentType
  applicationName.value = params.applicationName

  if (applicationActionConfirmModalRef.value) {
    applicationActionConfirmModalRef.value.setInit()
    applicationActionConfirmModalRef.value._getReasonList(params.operation)
  }
}

const openDetailModal = (cell: any) => {
  const rowData = cell.getRow().getData()
  const deploymentId = rowData.deploymentHistoryId || rowData.deploymentId
  if (!deploymentId) {
    return
  }

  selectedDeploymentId.value = deploymentId
  
  // Bootstrap 모달 열기
  const modal = document.getElementById(applicationStatusDetailModalId)
  if (modal) {
    try {
      if ((window as any).bootstrap && (window as any).bootstrap.Modal) {
        const modalInstance = new (window as any).bootstrap.Modal(modal)
        modalInstance.show()
        
        // 모달이 완전히 열린 후 API 호출 (약간의 지연 후)
        setTimeout(() => {
          if (applicationDetailModalRef.value) {
            applicationDetailModalRef.value.refreshData(selectedDeploymentId.value)
          }
        }, 100)
      } else {
        // Fallback: 직접 모달 표시
        modal.style.display = 'block'
        modal.classList.add('show')
        document.body.classList.add('modal-open')
        
        // API 호출
        if (applicationDetailModalRef.value) {
          applicationDetailModalRef.value.refreshData(selectedDeploymentId.value)
        }
      }
    } catch (error) {
      console.error('Error opening detail modal:', error)
    }
  }
}


const infraFormatter = (cell: any) => {
  const rowData = cell.getRow().getData()
  const infraType = rowData.deploymentType
  const infraName = getInfraDisplayName(rowData)
  return `
    <div style="cursor: pointer;">
      <p style="margin: 0;">
        ${infraType} (${infraName})
      </p>
    </div>
  ` 
}

const getInfraDisplayName = (rowData: any) => {
  if (rowData.deploymentType === 'VM') {
    return [rowData.namespace, rowData.mciId, rowData.vmId].filter(Boolean).join(' / ') || '-'
  }
  if (rowData.deploymentType === 'K8S') {
    return [rowData.namespace, rowData.clusterName].filter(Boolean).join(' / ') || '-'
  }
  return rowData.vmId || rowData.clusterName || '-'
}

const isActionDisabledStatus = (status: string) =>
  isApplicationActionDisabledStatus(status)

/**
 * @Title statusFormatter
 * @Desc Status Formatter
 */
const statusFormatter = (cell: any) => {
  const status = cell.getRow().getData().status
  const statusLabel = getApplicationStatusLabel(status)
  const statusClass = getApplicationStatusBadgeClass(status)

  return `
    <div style="cursor: pointer;">
      <span class="${statusClass}">
        ${statusLabel}
      </span>
    </div>`
}

/**
 * @Title actionButtonFormatter
 * @Desc 수정 / 삭제 버튼 Formatter
 */
const actionButtonFormatter = (cell: any) => {
  const status = cell.getRow().getData().status
  const disabledAttr = isActionDisabledStatus(status) ? 'disabled' : ''
  const normalizedStatus = String(status || '').trim().toUpperCase()
  const isStopped = normalizedStatus === 'STOP' || normalizedStatus === 'STOPPED'
  const lifecycleButtons = isStopped ? `
    <button
      class='btn btn-link text-primary px-2 py-1'
      id='start-btn'
      data-bs-toggle='modal'
      data-bs-target='#action-confirm'
      ${disabledAttr}>
      Start
    </button>
  ` : `
    <button
      class='btn btn-link text-primary px-2 py-1'
      id='restart-btn'
      data-bs-toggle='modal'
      data-bs-target='#action-confirm'
      ${disabledAttr}>
      Restart
    </button>
    <button
      class='btn btn-link text-warning px-2 py-1'
      id='stop-btn'
      data-bs-toggle='modal'
      data-bs-target='#action-confirm'
      ${disabledAttr}>
      Stop
    </button>
  `

  return `
  <div class='d-flex align-items-center gap-3 flex-nowrap'>
    ${lifecycleButtons}
    <button
      class='btn btn-link text-danger px-2 py-1'
      id='uninstall-btn'
      data-bs-toggle='modal'
      data-bs-target='#action-confirm'
      ${disabledAttr}>
      Uninstall
    </button>
    <button
      class='btn btn-link text-info px-2 py-1'
      id='rating-btn'
      data-bs-toggle='modal'
      data-bs-target='#rating-modal'
      ${disabledAttr}>
      Rating
    </button>
  </div>`;
}

defineExpose({
  refresh: _getApplicationsStatusList
})


</script>
