const STATUS_LABELS: Record<string, string> = {
  PREPARING_RUNTIME: 'Initializing',
  PREPARING_METRICS_SERVER: 'Initializing',
  PREPARING_INGRESS_NGINX: 'Initializing',
  DEPLOYING: 'Deploying',
  INSTALL: 'Installing',
  IN_PROGRESS: 'In Progress',
  PENDING: 'Pending',
  START: 'Starting',
  STARTING: 'Starting',
  RESTART: 'Restarting',
  RESTARTING: 'Restarting',
  RUN: 'Running',
  RUNNING: 'Running',
  SUCCESS: 'Running',
  COMPLETED: 'Completed',
  STOP: 'Stopped',
  STOPPED: 'Stopped',
  UNINSTALL: 'Uninstalling',
  UNINSTALLED: 'Uninstalled',
  NOT_FOUND: 'Not Found',
  UNKNOWN: 'Unknown',
  IMAGE_PULL_ERROR: 'Image Pull Error',
  FAILED: 'Failed',
  ERROR: 'Error'
}

const PROGRESS_STATUSES = new Set([
  'PREPARING_RUNTIME',
  'PREPARING_METRICS_SERVER',
  'PREPARING_INGRESS_NGINX',
  'DEPLOYING',
  'IN_PROGRESS',
  'INSTALL',
  'START',
  'STARTING',
  'RESTART',
  'RESTARTING'
])
const SUCCESS_STATUSES = new Set(['RUN', 'RUNNING', 'SUCCESS', 'COMPLETED'])
const WARNING_STATUSES = new Set(['NOT_FOUND', 'PENDING', 'UNKNOWN'])
const TERMINAL_STATUSES = new Set(['STOP', 'STOPPED', 'UNINSTALL', 'UNINSTALLED'])
const DANGER_STATUSES = new Set(['FAILED', 'ERROR', 'IMAGE_PULL_ERROR'])
const ACTION_DISABLED_STATUSES = new Set([
  'PREPARING_RUNTIME',
  'PREPARING_METRICS_SERVER',
  'PREPARING_INGRESS_NGINX',
  'DEPLOYING',
  'IN_PROGRESS',
  'INSTALL',
  'START',
  'STARTING',
  'RESTART',
  'RESTARTING',
  'UNINSTALL',
  'UNINSTALLED',
  'FAILED'
])

const HEALTH_CHECKING_STATUSES = new Set([
  'PREPARING_RUNTIME',
  'PREPARING_METRICS_SERVER',
  'PREPARING_INGRESS_NGINX',
  'DEPLOYING',
  'INSTALL',
  'IN_PROGRESS',
  'PENDING',
  'START',
  'STARTING',
  'RESTART',
  'RESTARTING',
  'RUN',
  'RUNNING',
  'SUCCESS'
])

export interface ApplicationHealthDisplay {
  label: 'Healthy' | 'Unhealthy' | 'Checking' | 'Unknown'
  className: string
}

const normalizeStatus = (status: string | null | undefined) => String(status || '').trim().toUpperCase()

export const getApplicationStatusLabel = (status: string | null | undefined) => {
  const normalized = normalizeStatus(status)
  if (!normalized) return '-'

  return STATUS_LABELS[normalized] || status || '-'
}

export const getApplicationStatusBadgeClass = (status: string | null | undefined) => {
  const normalized = normalizeStatus(status)

  if (SUCCESS_STATUSES.has(normalized)) return 'badge bg-success'
  if (PROGRESS_STATUSES.has(normalized)) return 'badge bg-primary'
  if (WARNING_STATUSES.has(normalized)) return 'badge bg-warning'
  if (TERMINAL_STATUSES.has(normalized)) return 'badge bg-secondary'
  if (DANGER_STATUSES.has(normalized)) return 'badge bg-danger'
  return 'badge bg-secondary'
}

export const getApplicationStatusIndicatorClass = (status: string | null | undefined) => {
  const normalized = normalizeStatus(status)

  if (SUCCESS_STATUSES.has(normalized)) return 'status status-green'
  if (PROGRESS_STATUSES.has(normalized)) return 'status status-primary'
  if (WARNING_STATUSES.has(normalized)) return 'status status-yellow'
  if (TERMINAL_STATUSES.has(normalized)) return 'status status-secondary'
  if (DANGER_STATUSES.has(normalized)) return 'status status-red'
  return 'status status-secondary'
}

export const isApplicationActionDisabledStatus = (status: string | null | undefined) => {
  return ACTION_DISABLED_STATUSES.has(normalizeStatus(status))
}

// A failed installation can leave managed resources pending cleanup. Keep
// lifecycle actions disabled, but allow the existing authorized uninstall flow.
export const isApplicationUninstallDisabledStatus = (status: string | null | undefined) => {
  return normalizeStatus(status) !== 'FAILED' && isApplicationActionDisabledStatus(status)
}

export const getApplicationHealthDisplay = (
  healthCheck: boolean | null | undefined,
  status: string | null | undefined
): ApplicationHealthDisplay => {
  if (healthCheck === true) {
    return { label: 'Healthy', className: 'text-success' }
  }
  if (healthCheck === false) {
    return { label: 'Unhealthy', className: 'text-danger' }
  }
  if (HEALTH_CHECKING_STATUSES.has(normalizeStatus(status))) {
    return { label: 'Checking', className: 'text-warning' }
  }
  return { label: 'Unknown', className: 'text-muted' }
}
