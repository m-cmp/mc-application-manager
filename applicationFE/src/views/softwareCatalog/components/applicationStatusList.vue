<template>
  <div class="card card-flush w-100">
    <div class="page-header page-wrapper">
      <div class="row align-items-center">
        <div class="card-header d-flex" style="justify-content: space-between;">
          <h3 class="card-title"><strong>Apps Status</strong></h3>
          <div class="btn-list">
            <span class="me-2">{{ refreshTime }}</span>
            <a class="btn btn-outline-primary d-none d-sm-inline-block" @click="_getApplicationsStatusList">
              <IconRefresh class="icon icon-tabler" :size="20" stroke-width="1" />
              Refresh
            </a>
          </div>
        </div>
      </div>
    </div>
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
    :deploymentId="selectedDeploymentId"
    />
</template>
<script setup lang="ts">
import Tabulator from '@/components/Table/Tabulator.vue'
import { ref, onMounted } from 'vue';
import type { ApplicationStatus } from '@/views/type/type'
import type { ColumnDefinition } from 'tabulator-tables';
import { useToast } from 'vue-toastification';
import { getApplicationsStatus } from '@/api/softwareCatalog';
import { IconRefresh } from '@tabler/icons-vue'
import ApplicationActionConfirm from './applicationActionConfirm.vue';
import ApplicationRatingModal from './applicationRatingModal.vue';
import ApplicationDetailModal from './applicationDetailModal.vue';

const toast = useToast()
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
const applicationDetailModalRef = ref()
const applicationActionConfirmModalRef = ref()
/**
 * @Title Life Cycle
 * @Desc 컬럼 set Callback 함수 호출 / ApplicationStatusList Callback 함수 호출
 */
onMounted(async () => {
  setColumns()
  await _getApplicationsStatusList()
})

/**_getApplicationsStatusList
 * @Title _getApplicationsStatusList
 * @Desc ApplicationStatus List Callback 함수 / ApplicationStatus List api 호출
 */
const _getApplicationsStatusList = async () => {
  try {
    initData()

    const { data } = await getApplicationsStatus()

    if (data) {
      applicationsStatusList.value = data
    }

    else {
      applicationsStatusList.value = []
    }

  } catch (error) {
    console.log(error)
    toast.error('Unable to retrieve data')
  }
}

const initData = () => {
  applicationsStatusList.value = []

  const now = new Date();
  const options = { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false } as any;

  refreshTime.value = now.toLocaleDateString('ko-KR', options)
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
      width: '25%',
      formatter: infraFormatter,
      cellClick: function (e, cell) {
        openDetailModal(cell)
      }
    },
    {
      title: "Application",
      field: "applicationName",
      width: '15%',
      cellClick: function (e, cell) {
        openDetailModal(cell)
      },
      formatter: function(cell) {
        return `<div style="cursor: pointer; color:">${cell.getValue()}</div>`
      }
    },
    {
      title: "Status",
      width: '13%',
      formatter: statusFormatter,
      cellClick: function (e, cell) {
        openDetailModal(cell)
      }
    },
    
    {
      title: "CheckedAt",
      field: "checkedAt",
      width: '17%',
      cellClick: function (e, cell) {
        openDetailModal(cell)
      },
      formatter: function(cell) {
        return `<div style="cursor: pointer;">${cell.getValue()}</div>`
      }
    },
    {
      title: "Action",
      width: '30%',
      formatter: actionButtonFormatter,
      cellClick: async function (e, cell) {
        const target = e.target as HTMLElement;
        const btnFlag = target?.getAttribute('id')
        const applicationStatusId = cell.getRow().getData().id
        const deploymentType = cell.getRow().getData().deploymentType
        const applicationName = cell.getRow().getData().applicationName

        const params = {
          operation: '' as string,
          applicationStatusId: applicationStatusId as number,
          deploymentType: deploymentType as string,
          applicationName: applicationName as string
        }
        if (btnFlag === 'restart-btn') {
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
  selectedDeploymentId.value = rowData.deploymentId || rowData.id
  
  // Bootstrap 모달 열기
  const modal = document.getElementById('application-detail-modal')
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
  const infraType = cell.getRow().getData().deploymentType
  const infraName =
    cell.getRow().getData().vmId ? cell.getRow().getData().vmId :
    cell.getRow().getData().clusterName ? cell.getRow().getData().clusterName : '-'
  return `
    <div style="cursor: pointer;">
      <p style="margin: 0;">
        ${infraType} (${infraName})
      </p>
    </div>
  ` 
}

/**
 * @Title statusFormatter
 * @Desc Status Formatter
 */
const statusFormatter = (cell: any) => {
  const status = cell.getRow().getData().status
  if (status === 'RUNNING') {
    return `
      <div style="cursor: pointer;">
        <span class="status status-green">  
          <span class="status-dot"></span>
            ${status}
        </span>
      </div>`
  }
  else if (status === 'RESTART' || status === 'IN_PROGRESS' ) {
  return `
    <div style="cursor: pointer;">
      <span class="status status-primary">  
        <span class="status-dot"></span>
          ${status}
      </span>
    </div>`
  }
  else if (status === 'NOT_FOUND' ) {
  return `
    <div style="cursor: pointer;">
      <span class="status status-yellow">  
        <span class="status-dot"></span>
          ${status}
      </span>
    </div>`
  }
  else {
    return `
  <div style="cursor: pointer;">
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
      data-bs-target='#action-confirm'>
      Restart
    </button>
    <button
      class='btn btn-ghost-warning d-none d-sm-inline-block'
      id='stop-btn'
      data-bs-toggle='modal' 
      data-bs-target='#action-confirm'>
      Stop
    </button>
    <button
      class='btn btn-ghost-danger d-none d-sm-inline-block'
      id='uninstall-btn'
      data-bs-toggle='modal' 
      data-bs-target='#action-confirm'>
      Uninstall
    </button>
    <button
      class='btn btn-ghost-info d-none d-sm-inline-block'
      id='rating-btn'
      data-bs-toggle='modal' 
      data-bs-target='#rating-modal'>
      Rating
    </button>
  </div>`;
}


</script>
