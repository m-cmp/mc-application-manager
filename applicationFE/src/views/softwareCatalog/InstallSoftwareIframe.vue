<template>
  <main class="install-page">
    <header class="install-page-header">
      <div>
        <p class="text-uppercase text-muted small mb-1">MC Application Manager</p>
        <h1 class="h2 mb-1">Install Software</h1>
        <p class="text-muted mb-0">The target is fixed by the calling Workload screen.</p>
      </div>
    </header>

    <div v-if="!targetResult.ok" class="alert alert-danger" role="alert">
      <strong>Invalid installation request.</strong>
      <div>{{ targetResult.error }}</div>
    </div>

    <template v-else>
      <section class="target-summary" aria-label="Fixed deployment target">
        <div>
          <span class="target-label">Target type</span>
          <strong>{{ targetResult.target.targetType }}</strong>
        </div>
        <div v-if="targetResult.target.targetType === 'VM'">
          <span class="target-label">{{ vmTargetLabelTitle }}</span>
          <strong>{{ vmTargetLabel }}</strong>
        </div>
        <div v-else>
          <span class="target-label">Cluster</span>
          <strong>{{ targetResult.target.clusterId }}</strong>
        </div>
      </section>

      <div v-if="!contextReady" class="alert alert-info" role="status">
        Waiting for Workspace and Project context from MC-WEB-CONSOLE…
      </div>

      <ApplicationInstallationForm
        v-else
        form-id="install-sw-iframe-form"
        title="Application Installation"
        :ns-id="projectNamespace"
        :embedded="true"
        :target-type="targetResult.target.targetType"
        :target-mci-id="targetResult.target.mciId"
        :target-vm-id="targetResult.target.vmId"
        :target-node-group-id="targetResult.target.nodeGroupId"
        :target-cluster-id="targetResult.target.clusterId"
        @ready="handleFormReady"
        @deployment-event="handleDeploymentEvent"
        @cancel="postParent('CANCELLED')" />
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { parseInstallTarget } from '@/integration/installTarget'
import ApplicationInstallationForm from '@/views/softwareCatalog/components/applicationInstallationForm.vue'

const INSTALL_EVENT_TYPE = 'MCMP_AM_INSTALL_EVENT'
const userStore = useUserStore()
const targetResult = parseInstallTarget(window.location.search)

const normalizeValue = (value: unknown) => {
  const normalized = String(value ?? '').trim()
  return ['undefined', 'null'].includes(normalized.toLowerCase()) ? '' : normalized
}

const projectNamespace = computed(() => normalizeValue(userStore.getNsId()))
const contextReady = computed(() => Boolean(
  normalizeValue(userStore.accessToken)
  && normalizeValue(userStore.workspaceInfo?.id ?? userStore.workspaceInfo?.Id)
  && normalizeValue(userStore.projectInfo?.id ?? userStore.projectInfo?.Id)
  && projectNamespace.value
))

const vmTargetLabel = computed(() => {
  if (!targetResult.ok || targetResult.target.targetType !== 'VM') return ''
  return [
    targetResult.target.mciId,
    targetResult.target.nodeGroupId,
    targetResult.target.vmId
  ].filter(Boolean).join(' / ')
})

const vmTargetLabelTitle = computed(() => {
  if (!targetResult.ok || targetResult.target.targetType !== 'VM') return ''
  if (!targetResult.target.vmId) return 'Infra / NodeGroup'
  return targetResult.target.nodeGroupId ? 'Infra / NodeGroup / VM' : 'Infra / VM'
})

const targetSummary = () => {
  if (!targetResult.ok) return undefined
  const target = targetResult.target
  return target.targetType === 'VM'
    ? {
        targetType: target.targetType,
        mciId: target.mciId,
        ...(target.nodeGroupId ? { nodeGroupId: target.nodeGroupId } : {}),
        ...(target.vmId ? { vmId: target.vmId } : {})
      }
    : { targetType: target.targetType, clusterId: target.clusterId }
}

const postParent = (status: string, detail: Record<string, unknown> = {}) => {
  if (window.parent === window) return

  // Status messages intentionally contain no access token or other credential.
  window.parent.postMessage({
    type: INSTALL_EVENT_TYPE,
    version: 1,
    requestId: targetResult.ok ? targetResult.target.requestId : '',
    status,
    target: targetSummary(),
    ...detail
  }, '*')
}

const handleFormReady = () => {
  postParent('FORM_READY')
}

const handleDeploymentEvent = (payload: Record<string, unknown>) => {
  const status = normalizeValue(payload.status)
  if (!status) return

  const detail: Record<string, unknown> = {}
  if (payload.deploymentId) detail.deploymentId = payload.deploymentId
  if (payload.message) detail.message = payload.message
  postParent(status, detail)
}

watch(contextReady, (ready, wasReady) => {
  if (ready && !wasReady) postParent('CONTEXT_READY')
}, { immediate: true })

onMounted(() => {
  postParent('IFRAME_READY')
  if (!targetResult.ok) {
    postParent('CONFIGURATION_ERROR', { message: targetResult.error })
  }
})
</script>

<style scoped>
.install-page {
  min-height: 100vh;
  padding: 24px;
  background: #f6f8fb;
}

.install-page-header,
.target-summary {
  width: 100%;
  max-width: 960px;
  margin: 0 auto 16px;
}

.target-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  padding: 14px 18px;
  border: 1px solid #dce2e9;
  border-radius: 8px;
  background: #fff;
}

.target-summary > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.target-label {
  color: #667085;
  font-size: 0.75rem;
  text-transform: uppercase;
}

.alert {
  width: 100%;
  max-width: 960px;
  margin: 0 auto 16px;
}

@media (max-width: 640px) {
  .install-page {
    padding: 12px;
  }
}
</style>
