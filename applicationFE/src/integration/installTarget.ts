export type InstallTargetType = 'VM' | 'K8S'

export interface InstallTarget {
  requestId: string
  targetType: InstallTargetType
  mciId: string
  vmId: string
  nodeGroupId: string
  clusterId: string
}

export interface InstallTargetVm {
  id?: string
  name?: string
  subGroupId?: string
  status?: string
}

export type InstallVmTargetResolution =
  | {
      ok: true
      mode: 'VM'
      vm: InstallTargetVm
      vmId: string
      nodeGroupId: string
    }
  | {
      ok: true
      mode: 'NODE_GROUP'
      members: InstallTargetVm[]
      runningVmIds: string[]
      nodeGroupId: string
    }
  | { ok: false; error: string }

export type InstallTargetParseResult =
  | { ok: true; target: InstallTarget }
  | { ok: false; error: string }

const TARGET_KEYS = ['targetType', 'requestId', 'mciId', 'vmId', 'nodeGroupId', 'clusterId'] as const
const FORBIDDEN_KEYS = ['namespace', 'namespaceId'] as const
const MAX_IDENTIFIER_LENGTH = 200
const PATH_IDENTIFIER_CHARACTERS = /[/?#]/

const hasUnsafeIdentifierCharacters = (value: string) => {
  return PATH_IDENTIFIER_CHARACTERS.test(value)
    || Array.from(value).some((character) => {
      const code = character.charCodeAt(0)
      return code <= 31 || code === 127
    })
}

const getSingleValue = (params: URLSearchParams, key: string) => {
  const values = params.getAll(key)
  if (values.length > 1) {
    return { ok: false as const, error: `Query parameter "${key}" must be provided only once.` }
  }
  return { ok: true as const, value: String(values[0] || '').trim() }
}

const validateIdentifier = (name: string, value: string, required = false) => {
  if (!value) {
    return required ? `${name} is required.` : ''
  }
  if (value.length > MAX_IDENTIFIER_LENGTH) {
    return `${name} must be ${MAX_IDENTIFIER_LENGTH} characters or fewer.`
  }
  if (hasUnsafeIdentifierCharacters(value)) {
    return `${name} contains unsupported path characters.`
  }
  return ''
}

export const parseInstallTarget = (
  input: string | URLSearchParams,
  generateRequestId: () => string = () => `am-install-${Date.now()}`
): InstallTargetParseResult => {
  const params = typeof input === 'string'
    ? new URLSearchParams(input.startsWith('?') ? input.slice(1) : input)
    : input

  for (const key of FORBIDDEN_KEYS) {
    if (params.has(key)) {
      return {
        ok: false,
        error: `${key} must not be supplied in the URL. Namespace comes from the selected Project context.`
      }
    }
  }

  for (const key of params.keys()) {
    if (!TARGET_KEYS.includes(key as (typeof TARGET_KEYS)[number])) {
      return { ok: false, error: `Unsupported query parameter "${key}".` }
    }
  }

  const values = {} as Record<(typeof TARGET_KEYS)[number], string>
  for (const key of TARGET_KEYS) {
    const result = getSingleValue(params, key)
    if (!result.ok) return result
    values[key] = result.value
  }

  const targetType = values.targetType.toUpperCase()
  if (targetType !== 'VM' && targetType !== 'K8S') {
    return { ok: false, error: 'targetType must be VM or K8S.' }
  }

  const requestId = values.requestId || generateRequestId()
  const commonErrors = [
    validateIdentifier('requestId', requestId, true),
    validateIdentifier('mciId', values.mciId),
    validateIdentifier('vmId', values.vmId),
    validateIdentifier('nodeGroupId', values.nodeGroupId),
    validateIdentifier('clusterId', values.clusterId)
  ].filter(Boolean)
  if (commonErrors.length > 0) {
    return { ok: false, error: commonErrors[0] }
  }

  if (targetType === 'VM') {
    const vmErrors = [
      validateIdentifier('mciId', values.mciId, true)
    ].filter(Boolean)
    if (vmErrors.length > 0) return { ok: false, error: vmErrors[0] }
    if (!values.vmId && !values.nodeGroupId) {
      return { ok: false, error: 'vmId or nodeGroupId is required.' }
    }
    if (values.clusterId) {
      return { ok: false, error: 'clusterId cannot be used with a VM target.' }
    }
  }

  if (targetType === 'K8S') {
    const clusterError = validateIdentifier('clusterId', values.clusterId, true)
    if (clusterError) return { ok: false, error: clusterError }
    if (values.mciId || values.vmId) {
      return { ok: false, error: 'mciId and vmId cannot be used with a K8S target.' }
    }
    if (values.nodeGroupId) {
      return { ok: false, error: 'nodeGroupId cannot be used with a K8S target.' }
    }
  }

  return {
    ok: true,
    target: {
      requestId,
      targetType,
      mciId: values.mciId,
      vmId: values.vmId,
      nodeGroupId: values.nodeGroupId,
      clusterId: values.clusterId
    }
  }
}

const getVmIdentifier = (vm: InstallTargetVm) => String(vm?.id || vm?.name || '').trim()

const matchesVmIdentifier = (vm: InstallTargetVm, targetVmId: string) => {
  const expected = String(targetVmId || '').trim().toLowerCase()
  return [vm?.id, vm?.name]
    .map((value) => String(value || '').trim().toLowerCase())
    .filter(Boolean)
    .includes(expected)
}

/**
 * Resolve a fixed VM iframe target against the authoritative MCI response.
 *
 * A caller may keep using the legacy MCI + VM contract. When nodeGroupId is
 * also present it is used to verify the selected VM's membership. Omitting
 * vmId switches the target to all currently running VMs in the NodeGroup.
 */
export const resolveInstallVmTarget = (
  vms: InstallTargetVm[],
  target: Pick<InstallTarget, 'vmId' | 'nodeGroupId'>
): InstallVmTargetResolution => {
  const availableVms = Array.isArray(vms) ? vms : []
  const vmId = String(target?.vmId || '').trim()
  const nodeGroupId = String(target?.nodeGroupId || '').trim()

  if (vmId) {
    const vm = availableVms.find((candidate) => matchesVmIdentifier(candidate, vmId))
    if (!vm) {
      return { ok: false, error: `VM "${vmId}" was not found.` }
    }
    if (nodeGroupId && String(vm.subGroupId || '').trim() !== nodeGroupId) {
      return {
        ok: false,
        error: `VM "${vmId}" does not belong to NodeGroup "${nodeGroupId}".`
      }
    }
    return {
      ok: true,
      mode: 'VM',
      vm,
      vmId: getVmIdentifier(vm),
      nodeGroupId
    }
  }

  const members = availableVms.filter(
    (vm) => String(vm?.subGroupId || '').trim() === nodeGroupId
  )
  if (members.length === 0) {
    return { ok: false, error: `NodeGroup "${nodeGroupId}" was not found.` }
  }

  const runningVmIds = Array.from(new Set(members
    .filter((vm) => String(vm?.status || '').trim().toUpperCase() === 'RUNNING')
    .map(getVmIdentifier)
    .filter(Boolean)))
  if (runningVmIds.length === 0) {
    return { ok: false, error: `NodeGroup "${nodeGroupId}" has no running VMs.` }
  }

  return {
    ok: true,
    mode: 'NODE_GROUP',
    members,
    runningVmIds,
    nodeGroupId
  }
}
