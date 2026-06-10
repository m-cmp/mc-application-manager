const STATUS_LABELS: Record<string, string> = {
  PREPARING_RUNTIME: 'Preparing Runtime',
  DEPLOYING: 'Deploying',
  INSTALL: 'Deployment Submitted',
  IN_PROGRESS: 'In Progress',
  PENDING: 'Pending',
  RESTART: 'Restarting',
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
  FAILED: 'Failed',
  ERROR: 'Error'
}

const PROGRESS_STATUSES = new Set(['PREPARING_RUNTIME', 'DEPLOYING', 'IN_PROGRESS', 'INSTALL', 'RESTART'])
const SUCCESS_STATUSES = new Set(['RUN', 'RUNNING', 'SUCCESS', 'COMPLETED'])
const WARNING_STATUSES = new Set(['NOT_FOUND', 'PENDING', 'UNKNOWN'])
const TERMINAL_STATUSES = new Set(['STOP', 'STOPPED', 'UNINSTALL', 'UNINSTALLED'])
const DANGER_STATUSES = new Set(['FAILED', 'ERROR'])
const ACTION_DISABLED_STATUSES = new Set(['PREPARING_RUNTIME', 'DEPLOYING', 'IN_PROGRESS', 'UNINSTALL', 'UNINSTALLED'])

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
