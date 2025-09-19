<template>
  <div class="modal modal-blur fade" id="application-detail-modal" tabindex="-1" role="dialog" aria-hidden="true">
    <div class="modal-dialog modal-xl modal-dialog-centered" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">Application Detail</h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" @click="closeModal"></button>
        </div>
        <div class="modal-body" style="max-height: 80vh; overflow-y: auto;">
          <div v-if="loading" class="text-center">
            <div class="spinner-border" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
          </div>
          
          <div v-else-if="applicationDetail">
            <!-- Application Overview -->
            <div class="mb-4">
              <h6 class="text-primary">Application Overview</h6>
              <div class="row">
                <div class="col-md-2 text-center">
                  <img v-if="applicationDetail.logoUrlLarge" :src="applicationDetail.logoUrlLarge" 
                       :alt="applicationDetail.catalogName" class="img-fluid" style="max-height: 80px;">
                </div>
                <div class="col-md-10">
                  <h5>{{ applicationDetail.catalogName }}</h5>
                  <p class="text-muted">{{ applicationDetail.catalogDescription }}</p>
                  <div class="row">
                    <div class="col-md-3">
                      <strong>Category:</strong> {{ applicationDetail.catalogCategory }}
                    </div>
                    <div class="col-md-3">
                      <strong>Default Port:</strong> {{ applicationDetail.defaultPort }}
                    </div>
                    <div class="col-md-3">
                      <strong>Status:</strong> 
                      <span :class="getStatusClass(applicationDetail.applicationStatus)">
                        {{ applicationDetail.applicationStatus }}
                      </span>
                    </div>
                    <div class="col-md-3">
                      <strong>Health Check:</strong> 
                      <span :class="applicationDetail.healthCheck ? 'text-success' : 'text-danger'">
                        {{ applicationDetail.healthCheck ? 'Healthy' : 'Unhealthy' }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Performance Metrics -->
            <div class="mb-4">
              <h6 class="text-primary">Performance Metrics</h6>
              <div class="row">
                <div class="col-md-3">
                  <div class="card text-center">
                    <div class="card-body">
                      <h5 class="card-title">{{ applicationDetail.cpuUsage }}%</h5>
                      <p class="card-text">CPU Usage</p>
                    </div>
                  </div>
                </div>
                <div class="col-md-3">
                  <div class="card text-center">
                    <div class="card-body">
                      <h5 class="card-title">{{ applicationDetail.memoryUsage }}%</h5>
                      <p class="card-text">Memory Usage</p>
                    </div>
                  </div>
                </div>
                <div class="col-md-3">
                  <div class="card text-center">
                    <div class="card-body">
                      <h5 class="card-title">{{ formatBytes(applicationDetail.networkIn) }}</h5>
                      <p class="card-text">Network In</p>
                    </div>
                  </div>
                </div>
                <div class="col-md-3">
                  <div class="card text-center">
                    <div class="card-body">
                      <h5 class="card-title">{{ formatBytes(applicationDetail.networkOut) }}</h5>
                      <p class="card-text">Network Out</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Action History -->
            <div class="mb-4">
              <h6 class="text-primary">Action History</h6>
              <div class="table-responsive">
                <table class="table table-sm">
                  <thead>
                    <tr>
                      <th>Action</th>
                      <th>Timestamp</th>
                      <th>User</th>
                      <th>Status</th>
                      <th>Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="applicationDetail.operationHistories.length === 0">
                      <td colspan="5" class="text-center text-muted">No action history available</td>
                    </tr>
                    <tr v-for="history in applicationDetail.operationHistories" :key="history.id">
                      <td>{{ history.operationType }}</td>
                      <td>{{ history.executedAt }}</td>
                      <td>{{ history.executedBy || 'system' }}</td>
                      <td>
                        <span :class="getStatusClass(history.status)">
                          {{ history.status }}
                        </span>
                      </td>
                      <td>{{ history.detailReason || history.reason }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Error Logs -->
            <div class="mb-4">
              <h6 class="text-primary">Error Logs</h6>
              <div class="table-responsive">
                <table class="table table-sm">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>Error Code</th>
                      <th>Severity</th>
                      <th>Module</th>
                      <th>Description</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-if="applicationDetail.errorLogs.length === 0">
                      <td colspan="5" class="text-center text-muted">No error logs available</td>
                    </tr>
                    <tr v-for="log in applicationDetail.errorLogs" :key="log.id">
                      <td>{{ log.loggedAt }}</td>
                      <td>{{ log.errorCode }}</td>
                      <td>
                        <span :class="getSeverityClass(log.severity)">
                          {{ log.severity }}
                        </span>
                      </td>
                      <td>{{ log.module }}</td>
                      <td>{{ log.logMessage }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Deployment History -->
            <div class="mb-4">
              <h6 class="text-primary">Deployment History</h6>
              
              <!-- Basic Deployment Information -->
              <div class="mb-3">
                <h6>Basic Deployment Information</h6>
                <div class="table-responsive">
                  <table class="table table-sm">
                    <thead>
                      <tr>
                        <th>ID</th>
                        <th>Action Type</th>
                        <th>Executed At</th>
                        <th>Executed By</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>{{ applicationDetail.deploymentId }}</td>
                        <td>{{ applicationDetail.actionType }}</td>
                        <td>{{ applicationDetail.executedAt }}</td>
                        <td>{{ applicationDetail.executedBy || 'system' }}</td>
                        <td>
                          <span :class="getStatusClass(applicationDetail.status)">
                            {{ applicationDetail.status }}
                          </span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- Cloud & Cluster Information -->
              <div class="mb-3">
                <h6>Cloud & Cluster Information</h6>
                <div class="table-responsive">
                  <table class="table table-sm">
                    <thead>
                      <tr>
                        <th>Cloud Provider</th>
                        <th>Cloud Region</th>
                        <th>Cluster Name</th>
                        <th>Deployment Type</th>
                        <th>MCI ID</th>
                        <th>UID</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>{{ applicationDetail.cloudProvider }}</td>
                        <td>{{ applicationDetail.cloudRegion }}</td>
                        <td>{{ applicationDetail.clusterName || 'N/A' }}</td>
                        <td>{{ applicationDetail.deploymentType }}</td>
                        <td>{{ applicationDetail.mciId }}</td>
                        <td>{{ applicationDetail.vmId }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- Network & Service Information -->
              <div class="mb-3">
                <h6>Network & Service Information</h6>
                <div class="table-responsive">
                  <table class="table table-sm">
                    <thead>
                      <tr>
                        <th>Namespace</th>
                        <th>Pod Status</th>
                        <th>Public IP</th>
                        <th>Service Port</th>
                        <th>VM ID</th>
                        <th>Catalog ID</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>{{ applicationDetail.namespace }}</td>
                        <td>{{ applicationDetail.podStatus || applicationDetail.applicationStatus }}</td>
                        <td>{{ applicationDetail.publicIp }}</td>
                        <td>{{ applicationDetail.servicePort || applicationDetail.defaultPort }}</td>
                        <td>{{ applicationDetail.vmId }}</td>
                        <td>{{ applicationDetail.catalogName }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              <!-- Ingress Information -->
              <div class="mb-3" v-if="applicationDetail.ingressEnabled">
                <h6>Ingress Information</h6>
                <div class="table-responsive">
                  <table class="table table-sm">
                    <thead>
                      <tr>
                        <th>Enabled</th>
                        <th>Host</th>
                        <th>Path</th>
                        <th>Class</th>
                        <th>TLS Enabled</th>
                        <th>TLS Secret</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>
                          <span :class="applicationDetail.ingressEnabled ? 'text-success' : 'text-danger'">
                            {{ applicationDetail.ingressEnabled ? 'Yes' : 'No' }}
                          </span>
                        </td>
                        <td>{{ applicationDetail.ingressHost || 'N/A' }}</td>
                        <td>{{ applicationDetail.ingressPath || 'N/A' }}</td>
                        <td>{{ applicationDetail.ingressClass || 'N/A' }}</td>
                        <td>
                          <span :class="applicationDetail.ingressTlsEnabled ? 'text-success' : 'text-danger'">
                            {{ applicationDetail.ingressTlsEnabled ? 'Yes' : 'No' }}
                          </span>
                        </td>
                        <td>{{ applicationDetail.ingressTlsSecret || 'N/A' }}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            </div>

            <!-- Deployment Logs -->
            <div class="mb-4" v-if="applicationDetail.deploymentLogs && applicationDetail.deploymentLogs.length > 0">
              <h6 class="text-primary">Deployment Logs</h6>
              <div class="table-responsive">
                <table class="table table-sm">
                  <thead>
                    <tr>
                      <th>Timestamp</th>
                      <th>Type</th>
                      <th>Message</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="log in applicationDetail.deploymentLogs" :key="log.id">
                      <td>{{ log.loggedAt }}</td>
                      <td>
                        <span :class="getLogTypeClass(log.logType)">
                          {{ log.logType }}
                        </span>
                      </td>
                      <td>{{ log.logMessage }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <div v-else class="text-center text-muted">
            No data available
          </div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" data-bs-dismiss="modal" @click="closeModal">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useToast } from 'vue-toastification'
import { getApplicationDetail } from '@/api/softwareCatalog'

interface Props {
  deploymentId: number
}

const props = defineProps<Props>()
const emit = defineEmits(['close'])

const toast = useToast()
const loading = ref(false)
const applicationDetail = ref(null as any)

const loadApplicationDetail = async () => {
  if (!props.deploymentId) return
  
  loading.value = true
  try {
    const { data } = await getApplicationDetail(props.deploymentId)
    if (data) {
      applicationDetail.value = data.integratedInfo
    }
  } catch (error) {
    console.error('Failed to load application detail:', error)
    toast.error('Failed to load application detail')
  } finally {
    loading.value = false
  }
}

const getStatusClass = (status: string) => {
  switch (status?.toLowerCase()) {
    case 'success':
    case 'completed':
    case 'running':
      return 'badge bg-success'
    case 'failed':
    case 'error':
    case 'not_found':
      return 'badge bg-danger'
    case 'in_progress':
    case 'pending':
    case 'restart':
      return 'badge bg-warning'
    default:
      return 'badge bg-secondary'
  }
}

const getSeverityClass = (severity: string) => {
  switch (severity?.toLowerCase()) {
    case 'critical':
      return 'badge bg-danger'
    case 'warning':
      return 'badge bg-warning'
    case 'error':
      return 'badge bg-danger'
    default:
      return 'badge bg-info'
  }
}

const getLogTypeClass = (logType: string) => {
  switch (logType?.toLowerCase()) {
    case 'info':
      return 'badge bg-info'
    case 'warning':
      return 'badge bg-warning'
    case 'error':
      return 'badge bg-danger'
    case 'debug':
      return 'badge bg-secondary'
    default:
      return 'badge bg-primary'
  }
}

const formatBytes = (bytes: number) => {
  if (bytes === 0) return '0 Bytes'
  if (!bytes) return 'N/A'
  
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const closeModal = () => {
  const modal = document.getElementById('application-detail-modal')
  if (modal) {
    try {
      // Bootstrap 5를 사용하는 경우
      if ((window as any).bootstrap && (window as any).bootstrap.Modal) {
        const modalInstance = (window as any).bootstrap.Modal.getInstance(modal)
        if (modalInstance) {
          modalInstance.hide()
        } else {
          // 인스턴스가 없으면 새로 생성해서 닫기
          const newModalInstance = new (window as any).bootstrap.Modal(modal)
          newModalInstance.hide()
        }
      } else {
        // Bootstrap이 없거나 다른 경우 직접 모달 닫기
        modal.style.display = 'none'
        modal.classList.remove('show')
        modal.setAttribute('aria-hidden', 'true')
        modal.removeAttribute('aria-modal')
        document.body.classList.remove('modal-open')
        
        // backdrop 제거
        const backdrops = document.querySelectorAll('.modal-backdrop')
        backdrops.forEach(backdrop => backdrop.remove())
        
        // body의 overflow 스타일 복원
        document.body.style.overflow = ''
        document.body.style.paddingRight = ''
      }
    } catch (error) {
      console.error('Error closing modal:', error)
      // 에러 발생 시 강제로 모달 닫기
      modal.style.display = 'none'
      modal.classList.remove('show')
      document.body.classList.remove('modal-open')
      const backdrops = document.querySelectorAll('.modal-backdrop')
      backdrops.forEach(backdrop => backdrop.remove())
    }
  }
  
  // 데이터 초기화
  applicationDetail.value = null
  emit('close')
}

// Watch for deploymentId changes to reload data
import { watch } from 'vue'
watch(() => props.deploymentId, (newId) => {
  if (newId) {
    loadApplicationDetail()
  }
}, { immediate: true })
</script>

<style scoped>
.table th {
  font-weight: 600;
  background-color: #f8f9fa;
}

.table-responsive {
  border-radius: 6px;
  border: 1px solid #dee2e6;
}

.badge {
  font-size: 0.75rem;
}

h6 {
  color: #495057;
  font-weight: 600;
  margin-bottom: 0.75rem;
}

.text-primary {
  color: #0d6efd !important;
}

.card {
  border: 1px solid #dee2e6;
  border-radius: 8px;
  margin-bottom: 1rem;
}

.card-body {
  padding: 1rem;
}

.card-title {
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}

.card-text {
  color: #6c757d;
  font-size: 0.875rem;
  margin-bottom: 0;
}

.img-fluid {
  border-radius: 8px;
}

.row {
  margin-bottom: 1rem;
}

.col-md-3 strong,
.col-md-10 strong {
  color: #495057;
}
</style>
