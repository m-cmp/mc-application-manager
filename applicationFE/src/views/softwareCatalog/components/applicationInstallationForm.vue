<template>
  <div
    :class="embedded ? 'install-embedded' : 'modal fade'"
    :id="formId"
    :tabindex="embedded ? undefined : -1">
    <div
      class="modal-dialog modal-lg"
      :class="{ 'install-embedded-dialog': embedded }"
      role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            {{ modalTitle }}
          </h5>
          <button
            v-if="!embedded"
            type="button"
            class="btn-close"
            data-bs-dismiss="modal"
            aria-label="Close"
            @click="handleCancel"></button>
        </div>
        <div
          class="modal-body"
          :class="{ 'install-embedded-body': embedded }"
          :style="embedded ? undefined : 'max-height: calc(100vh - 200px);overflow-y: auto;'">

          <div v-if="deploymentCompleted" class="alert alert-success mb-0" role="status">
            <strong>Deployment completed.</strong>
            <p class="mb-0">Check the application's running status in Apps Status.</p>
          </div>
          <div v-if="interruptedDeployment" class="alert alert-warning" role="status">
            AM restarted while tracking this installation. Check Apps Status before starting another installation.
            <button type="button" class="btn btn-sm btn-outline-warning ms-2" :disabled="closingInterruptedTracking"
              @click="closeInterruptedTracking">Close interrupted tracking</button>
          </div>
          <fieldset v-show="!deploymentCompleted" :disabled="deploying || deploymentCompleted" class="border-0 p-0 m-0">
          <div v-if="hasProjectContext" class="alert alert-info py-2" role="status">
            Deployment targets are scoped to
            <strong>{{ projectContextLabel }}</strong>.
          </div>

          <div v-if="projectScopeError" class="alert alert-warning py-2" role="alert">
            {{ projectScopeError }}
          </div>

          <div v-if="isTargetLocked" class="alert alert-secondary py-2" role="status">
            The deployment target was fixed by the Workload screen:
            <strong>{{ lockedTargetLabel }}</strong>.
          </div>

          <div class="mb-3">
            <label class="form-label">Target Infra</label>
            <p
              v-if="modalTitle == 'Application Installation'"
              class="text-muted">
                Select the Infra what is the Infra will be installed
            </p>
            <p
              v-else-if="modalTitle == 'Application Uninstallation'"
              class="text-muted">
                Select the Infra what is the Infra will be uninstalled
            </p>
            <select
              class="form-select"
              id="infra"
              v-model="selectInfra"
              :disabled="isTargetLocked">
              <option
                v-for="infra in infraList"
                :value=infra.value
                :key="infra.value">
                  {{ infra.value }}
                </option>
            </select>
          </div>

          <!--
            ==============================================================================================
            ============================================= VM =============================================
            ==============================================================================================
          -->
          <template v-if="selectInfra == 'VM'">
            <div class="mb-3">

              <!-- VM :: Namespace -->
              <label class="form-label">Namespace</label>
              <p
                v-if="modalTitle == 'Application Installation'"
                class="text-muted">
                Select the namespace where the application will be installed</p>
              <p
                v-else-if="modalTitle == 'Application Uninstallation'"
                class="text-muted">
                Select the namespace where the application will be uninstalled</p>

              <template v-if="nsIdList.length > 0">
                <select
                  class="form-select"
                  id="vm-namespace"
                  v-model="selectNsId"
                  :disabled="isNamespaceLocked"
                  @change="onChangeNsId">
                  <option
                    v-for="ns in nsIdList"
                    :value="getNamespaceValue(ns)"
                    :key="getNamespaceValue(ns)">
                    {{ ns.name || ns.id }}
                  </option>
                </select>
              </template>

              <template v-else>
                <select
                  class="form-select"
                  id="vm-namespace-empty"
                  disabled>
                  <option value="">
                    No namespace available
                  </option>
                </select>
              </template>
            </div>

            <!-- VM :: Infra ID -->
            <div class="mb-3">
              <label class="form-label">Infra ID</label>
              <p
                v-if="modalTitle == 'Application Installation'"
                class="text-muted">
                Select the infra ID where the application will be deployed</p>
              <p
                v-else-if="modalTitle == 'Application Uninstallation'"
                class="text-muted">
                Remove the application and associated resources from the infra</p>
              <select
                class="form-select"
                id="vm-mci"
                :disabled="selectNsId == '' || isTargetLocked"
                v-model="selectMci"
                @change="onChangeMci">
                <option v-if="mciList.length === 0" value="">No infra available</option>
                <option
                  v-for="mci in mciList"
                  :value="mci.id || mci.name"
                  :key="mci.id || mci.name"
                  :title="mci.id || mci.name">
                    {{ mci.id || mci.name }}
                  </option>
              </select>
            </div>

            <!-- VM :: Deployment Target -->
            <div class="mb-3" v-if="modalTitle == 'Application Installation' && !isTargetLocked">
              <label class="form-label">Deployment Target</label>
              <p class="text-muted">Choose one VM or one CB-Tumblebug NodeGroup.</p>
              <div class="d-flex gap-3">
                <div class="form-check">
                  <input
                    class="form-check-input"
                    type="radio"
                    id="vm-target-single"
                    v-model="vmTargetMode"
                    value="VM">
                  <label class="form-check-label" for="vm-target-single">VM</label>
                </div>
                <div class="form-check">
                  <input
                    class="form-check-input"
                    type="radio"
                    id="vm-target-node-group"
                    v-model="vmTargetMode"
                    value="NODE_GROUP">
                  <label class="form-check-label" for="vm-target-node-group">NodeGroup (Standalone)</label>
                </div>
              </div>
            </div>

            <!-- VM :: VM Name -->
            <div class="mb-3" v-if="vmTargetMode === 'VM'">
              <label class="form-label">VM Name</label>
              <p
                class="text-muted">
                Select the virtual machine (VM) within the chosen multi-cloud infrastructure where the application will be deployed</p>
              <select
                class="form-select"
                id="vm-name"
                :disabled="selectMci == '' || isTargetLocked"
                v-model="selectVm"
                @change="onSelectVm">
                <option value="">Select VM</option>
                <option
                  v-for="vm in vmList"
                  :value="getVmValue(vm)"
                  :key="getVmValue(vm)">
                  {{ vm.name || vm.id }}
                </option>
              </select>

              <div class="mt-2" style="display: flex; gap: 10px; flex-wrap: wrap;" v-if="selectedVmList.length > 0">
                <label
                  v-for="(vmId, index) in selectedVmList"
                  :key="index"
                  class="form-check-label"
                  style="border: 1px solid #000; padding: 5px; border-radius: 5px; cursor: pointer;">
                  {{ vmId }}
                  <span
                    v-if="!isTargetLocked"
                    @click="removeVm(index)"
                    style="margin-left: 5px; font-weight: bold;">X</span>
                </label>
              </div>
            </div>

            <!-- VM :: NodeGroup -->
            <div class="mb-3" v-else>
              <label class="form-label">NodeGroup</label>
              <p class="text-muted">
                Deploy one independent application instance to every currently running VM in the selected NodeGroup.
              </p>
              <select
                class="form-select"
                id="vm-node-group"
                :disabled="selectMci == '' || isTargetLocked"
                v-model="selectVmNodeGroupId"
                @change="onSelectVmNodeGroup">
                <option value="">Select NodeGroup</option>
                <option
                  v-for="nodeGroup in vmNodeGroupOptions"
                  :key="nodeGroup.id"
                  :value="nodeGroup.id"
                  :disabled="nodeGroup.runningVmIds.length === 0">
                  {{ nodeGroup.id }} ({{ nodeGroup.runningVmIds.length }}/{{ nodeGroup.totalVmCount }} running)
                </option>
              </select>

              <div v-if="selectVmNodeGroupId && selectedNodeGroupVmIds.length > 0" class="mt-2">
                <div class="text-muted small mb-1">Deployment targets</div>
                <div class="d-flex gap-2 flex-wrap">
                  <span v-for="vmId in selectedNodeGroupVmIds" :key="vmId" class="badge bg-light text-dark">
                    {{ vmId }}
                  </span>
                </div>
              </div>
              <div v-else-if="selectVmNodeGroupId" class="text-warning mt-2">
                The selected NodeGroup has no running VMs.
              </div>
            </div>


            <!-- VM :: Deployment Type -->
            <div class="mb-3" v-if="vmTargetMode === 'VM'">
              <label class="form-label">Deployment Type</label>
              <p class="text-muted">Select the deployment type</p>
              <div style="display: flex; gap: 10px;">
                <div class="form-check">
                  <input class="form-check-input" type="radio" id="Standalone" v-model="selectDeploymentType" value="Standalone" :disabled="isTargetLocked">
                  <label class="form-check-label" for="Standalone">Standalone</label>
                </div>
                <div class="form-check" v-if="canSelectClustering">
                  <input class="form-check-input" type="radio" id="Clustering" v-model="selectDeploymentType" value="Clustering" :disabled="isTargetLocked">
                  <label class="form-check-label" for="Clustering">Clustering</label>
                </div>
              </div>
              <div class="form-text" v-if="!isTargetLocked">Clustering is available only for Redis and Elasticsearch server catalogs.</div>
            </div>
            <div class="mb-3" v-else>
              <label class="form-label">Deployment Type</label>
              <input type="text" class="form-control" value="Standalone" disabled>
              <div class="form-text">NodeGroup deployment does not use application clustering.</div>
            </div>

            <!-- VM :: Application -->
            <div class="mb-3">
              <label class="form-label">Application</label>
              <p class="text-muted">Select the application</p>
              <select
                class="form-select"
                v-model="inputApplications"
                @change="onChangeCatalog">
                <option v-for="(catalog, idx) in filteredCatalogList" :key="idx" :value="catalog.name">
                  [{{ catalogDisplayName(catalog, catalogList) }}] {{ catalog.packageInfo?.packageVersion || "latest" }}
                </option>
              </select>
            </div>

            <!-- VM :: Service Port -->
            <div class="mb-3">
              <label class="form-label">Port</label>
              <p class="text-muted">Docker host port. Security Group access is configured separately below.</p>
              <input type="number"  class="form-control" placeholder="8080"  v-model="inputServicePort">
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation'">
              <label class="form-label">Network Exposure</label>
              <select class="form-select" v-model="vmNetworkExposureMode">
                <option value="PRIVATE">Do not change Security Group (recommended)</option>
                <option value="RESTRICTED">Add restricted direct access</option>
              </select>
              <p class="text-muted mt-1 mb-0">
                Direct access appends one inbound TCP rule through Tumblebug. It never resaves existing rules.
              </p>

              <div class="mt-2" v-if="vmNetworkExposureMode === 'RESTRICTED'">
                <label class="form-label required">Allowed IPv4 CIDR</label>
                <input
                  type="text"
                  class="form-control"
                  placeholder="203.0.113.10/32"
                  v-model.trim="servicePortCidr">
                <p class="text-warning mt-1 mb-0">
                  Public any (0.0.0.0/0) is rejected. Broader rules already present in the Security Group are not removed.
                </p>
              </div>
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation'">
              <label class="form-label">Resource Type</label>
              <select class="form-select" v-model="selectedResourceType">
                <option value="GENERAL_PURPOSE">General Purpose</option>
                <option value="CPU_INTENSIVE">CPU Intensive</option>
                <option value="MEMORY_INTENSIVE">Memory Intensive</option>
              </select>
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation' && showObjectStorageConfig">
              <label class="form-label">Object Storage Configuration</label>
              <p class="text-muted">
                Select Object Storage resources already registered in Tumblebug. CSP access keys are not sent to JupyterLab.
              </p>

              <div class="form-check mb-2">
                <input class="form-check-input" type="checkbox" id="vmObjectStorageEnabled"
                  v-model="objectStorageData.enabled" :disabled="objectStorageRequired">
                <label class="form-check-label" for="vmObjectStorageEnabled">Enable Object Storage</label>
              </div>

              <div v-if="objectStorageData.enabled">
                <div class="d-flex align-items-center justify-content-between mb-2">
                  <label class="form-label mb-0 required">Registered Object Storage</label>
                  <button type="button" class="btn btn-sm btn-outline-secondary"
                    :disabled="registeredObjectStorageLoading" @click="fetchRegisteredObjectStorages">
                    Refresh
                  </button>
                </div>

                <div class="text-muted" v-if="registeredObjectStorageLoading">Loading Object Storage resources...</div>
                <div class="alert alert-danger py-2" v-else-if="registeredObjectStorageLoadError">
                  Object Storage resources could not be loaded from Tumblebug.
                </div>
                <div class="alert alert-warning py-2" v-else-if="registeredObjectStorageList.length === 0">
                  No Object Storage resource is registered in this namespace.
                </div>
                <div class="border rounded p-2" v-else>
                  <div class="form-check" v-for="storage in registeredObjectStorageList" :key="storage.id">
                    <input class="form-check-input" type="checkbox"
                      :id="`vm-object-storage-${storage.id}`"
                      :value="storage.id"
                      :disabled="String(storage.status || '').toLowerCase() !== 'available'"
                      v-model="objectStorageData.selectedStorageIds">
                    <label class="form-check-label" :for="`vm-object-storage-${storage.id}`">
                      {{ storage.name || storage.id }}
                      <span class="text-muted">
                        ({{ storage.provider || 'unknown CSP' }}{{ storage.region ? ` / ${storage.region}` : '' }} / {{ storage.status || 'unknown' }})
                      </span>
                    </label>
                  </div>
                </div>

                <div class="d-flex justify-content-between mt-2">
                  <div class="w-50 me-2">
                    <label class="form-label">Allowed Object Prefix</label>
                    <input type="text" class="form-control" placeholder="Optional: data/project-a/" v-model.trim="objectStorageData.prefix">
                  </div>
                  <div class="w-50 ms-2">
                    <label class="form-label">Access Mode</label>
                    <select class="form-select" v-model="objectStorageData.accessMode">
                      <option value="READ_ONLY">Read only</option>
                      <option value="READ_WRITE">Read and write</option>
                    </select>
                  </div>
                </div>

                <div class="mt-2">
                  <label class="form-label required">Jupyter Access Token</label>
                  <input type="password" class="form-control" placeholder="At least 12 characters" v-model="objectStorageData.jupyterToken" autocomplete="new-password">
                  <p class="text-muted mt-1 mb-0">
                    This is the Jupyter login token, not a CSP secret key. Use it at http://&lt;VM public IP&gt;:{{ inputServicePort }}.
                  </p>
                </div>

                <p class="text-muted mt-2 mb-0">
                  Jupyter requests short-lived presigned URLs through Application Manager. Multiple CSPs can be selected.
                </p>

                <div class="alert mt-3" :class="objectStorageCheckResult.success ? 'alert-success' : 'alert-danger'" v-if="objectStorageCheckResult">
                  <div>{{ objectStorageCheckResult.success ? 'Object Storage: SUCCESS' : 'Object Storage: FAILED' }}</div>
                  <ul class="mb-0 ps-3">
                    <li v-for="check in objectStorageCheckResult.checks" :key="check.name">
                      {{ check.name }} - {{ check.success ? 'OK' : 'FAIL' }}
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          </template>

          <!--
            ==============================================================================================
            ============================================ K8S =============================================
            ==============================================================================================
          -->
          <template v-else-if="selectInfra == 'K8S'">

            <!-- K8S :: Namespace -->
            <div class="mb-3">
              <label class="form-label">Namespace</label>
              <p
                v-if="modalTitle == 'Application Installation'"
                class="text-muted">Select the namespace where the application will be installed</p>
              <p
                v-else-if="modalTitle == 'Application Uninstallation'"
                class="text-muted">Select the namespace where the application will be uninstalled</p>

              <template v-if="nsIdList.length > 0">
                <select
                  class="form-select"
                  id="k8s-namespace"
                  v-model="selectNsId"
                  :disabled="isNamespaceLocked"
                  @change="onSelectNamespace">
                  <option
                    v-for="ns in nsIdList"
                    :value="getNamespaceValue(ns)"
                    :key="getNamespaceValue(ns)">
                    {{ ns.name || ns.id }}
                  </option>
                </select>
              </template>

              <template v-else>
                <select
                  class="form-select"
                  id="k8s-namespace-empty"
                  disabled>
                  <option value="">
                    No namespace available
                  </option>
                </select>
              </template>
            </div>

            <!-- K8S :: ClusterName -->
            <div class="mb-3">
              <label class="form-label">ClusterName</label>
              <p
                v-if="modalTitle == 'Application Installation'"
                class="text-muted">Select the name of the cluster where the application will be deployed</p>
              <p
                v-else-if="modalTitle == 'Application Uninstallation'"
                class="text-muted">Remove the application and associated resources from the multi-cloud infrastructure</p>

              <select
                class="form-select"
                id="k8s-cluster"
                :disabled="selectNsId == '' || isTargetLocked"
                v-model="selectCluster"
                @change="onChangeCluster">
                <option v-if="clusterList.length === 0" value="">No cluster available</option>
                <option
                  v-for="cluster in clusterList"
                  :value="getClusterValue(cluster)"
                  :key="getClusterValue(cluster)">
                  {{ cluster.name || cluster.id }}
                </option>
              </select>
            </div>

            <!-- K8S :: Helm Chart -->
            <div class="mb-3">
              <label class="form-label">Helm chart</label>
              <p class="text-muted">Select the application</p>
              <select class="form-select" v-model="inputApplications" @change="onChangeCatalog">
                <option v-for="(catalog, idx) in filteredCatalogList" :key="idx" :value="catalog.name">
                  [{{ catalogDisplayName(catalog, catalogList) }}] {{ catalog.helmChart?.chartVersion || catalog.packageInfo?.packageVersion || "latest" }}
                </option>
              </select>
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation'">
              <label class="form-label">Port</label>
              <p class="text-muted">Please enter a service port for the Kubernetes service</p>
              <input type="number" class="form-control" placeholder="80" v-model="inputServicePort" :disabled="isJupyterObjectStorageCatalog">
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation'">
              <label class="form-label">Resource Type</label>
              <select class="form-select" v-model="selectedResourceType">
                <option value="GENERAL_PURPOSE">General Purpose</option>
                <option value="CPU_INTENSIVE">CPU Intensive</option>
                <option value="MEMORY_INTENSIVE">Memory Intensive</option>
              </select>
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation' && showStorageClassConfig">
              <label class="form-label" :class="{ required: storageClassRequired }">Storage Class</label>
              <select
                class="form-select"
                v-model="selectedStorageClass"
                :disabled="storageClassSelectDisabled">
                <option value="" disabled>
                  {{ storageClassPlaceholder }}
                </option>
                <option
                  v-for="storageClass in storageClassList"
                  :key="storageClass.name"
                  :value="storageClass.name">
                  {{ storageClass.name }}{{ isRecommendedStorageClass(storageClass) ? ' (recommended)' : '' }}{{ storageClass.defaultClass ? ' (default)' : '' }}
                </option>
              </select>
              <p class="text-danger mt-1 mb-0" v-if="storageClassRequired && storageClassErrorMessage">
                {{ storageClassErrorMessage }}
              </p>
              <button type="button" class="btn btn-outline-secondary btn-sm mt-2" :disabled="storageClassLoading || storageCreating" @click="fetchStorageClasses(selectedStorageClass)">Refresh StorageClasses</button>
              <p v-if="storageSetupError" class="text-danger mt-2" role="alert">{{ storageSetupError }}</p>
              <div v-if="isNhnCluster && !storageClassLoadError && !storageClassList.length" class="border rounded p-2 mt-2">
                <div class="fw-bold mb-2">NHN block storage setup</div>
                <p role="status" class="mb-2">
                  {{ storageCapability?.message || (storageSetupError ? 'NHN storage setup is unavailable.' : 'Checking Cinder CSI and AM permissions...') }}
                </p>
                <div class="mb-2">
                  <span class="fw-semibold">1. Cinder CSI Plugin add-on</span>
                  <span v-if="storageCapability" class="badge ms-2" :class="storageCapability.driverReady ? 'bg-success' : 'bg-warning text-dark'">
                    {{ storageCapability.driverReady ? 'Ready' : 'Required' }}
                  </span>
                  <p v-if="storageCapability && !storageCapability.driverReady" class="text-muted mt-1 mb-0">
                    Install cinder-csi-plugin from the selected cluster's NHN NKS Add-ons page, wait until it is active, and refresh StorageClasses here.
                  </p>
                </div>
                <div v-if="storageCapability?.driverReady">
                  <div class="fw-semibold mb-2">2. Create StorageClass</div>
                  <label class="form-label">StorageClass name</label>
                  <input class="form-control" v-model="newStorageClassName" :disabled="storageCreating || !storageCapability.canCreate" maxlength="63">
                  <label class="form-label mt-2">Disk type</label>
                  <select class="form-select" v-model="newStorageDiskType" :disabled="storageCreating || !storageCapability.canCreate">
                    <option>General HDD</option><option>General SSD</option>
                  </select>
                  <p class="text-muted mt-2">Volumes are created when a Pod uses the claim. Retain policy keeps disks after claim deletion; remove unused disks separately.</p>
                  <button type="button" class="btn btn-outline-primary" :disabled="storageCreating || !storageCapability.canCreate || !newStorageClassName" @click="createNotebookStorageClass">{{ storageCreating ? 'Creating...' : 'Create NHN StorageClass' }}</button>
                </div>
              </div>
              <div v-if="isJupyterObjectStorageCatalog || isBuiltInPersistentCatalog" class="mt-2">
                <label class="form-label">{{ isJupyterObjectStorageCatalog ? 'Notebook' : 'Data' }} volume capacity (GiB)</label>
                <input type="number" class="form-control" v-model.number="notebookStorageGi" :min="selectedStorageMinimum" step="1">
                <p class="text-muted">Minimum {{ selectedStorageMinimum }} GiB for the known disk limits. Access mode: ReadWriteOnce. Provider quotas and disk availability are checked during provisioning.</p>
                <p v-if="isBuiltInPersistentCatalog" class="text-muted">Single instance with generated credentials in Secret &lt;release-name&gt;-auth (key: password). Data PVC and credentials are retained after uninstall; remove them separately when no longer needed.</p>
              </div>
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation' && ingressData.ingressEnabled">
              <label class="form-label required">Allowed IPv4 CIDR</label>
              <input class="form-control" placeholder="203.0.113.10/32" v-model.trim="servicePortCidr">
              <div class="form-check mt-2" v-if="!isIbmCluster">
                <input class="form-check-input" type="checkbox" id="k8sOpenIngress" v-model="k8sOpenIngress">
                <label class="form-check-label" for="k8sOpenIngress">Allow this CIDR on the worker Security Group (TCP 30880)</label>
              </div>
              <p v-if="isIbmCluster" class="text-muted mt-1">HTTP via the IBM-managed load balancer. Only the allowed client IP range can access this application.</p>
              <p v-else class="text-muted mt-1">Access: http://{{ ingressData.ingressHost || 'your-ingress-host' }}:30880. For testing, map this hostname to an accessible Kubernetes node public IP in your PC hosts file. With source-IP preservation, use a node running the Ingress Controller.</p>
            </div>
            <!-- K8S :: HPA -->
            <div class="mb-3" v-if="modalTitle == 'Application Installation'" >
              <label class="form-label">HPA Configuration</label>

              <!-- HPA Enabled -->
              <div class="mb-2">
                <div class="form-check">
                  <input
                    class="form-check-input"
                    type="checkbox"
                    id="hpaEnabled"
                    :disabled="isBuiltInPersistentCatalog"
                    v-model="hpaData.hpaEnabled">
                  <label class="form-check-label" for="hpaEnabled">
                    Enable HPA (Horizontal Pod Autoscaler)
                  </label>
                </div>
              </div>

              <!-- HPA Fields (shown when enabled) -->
              <div v-if="hpaData.hpaEnabled" class="d-flex justify-content-between">
                <!-- min Replicas -->
                <div>
                  <label class="form-label required">
                    minReplicas
                  </label>
                  <input
                    type="number"
                    class="form-control w-90-per"
                    placeholder="1"
                    v-model="hpaData.hpaMinReplicas" />
                </div>

                <!-- max Replicas -->
                <div>
                  <label class="form-label required">
                    maxReplicas
                  </label>
                  <input
                    type="number"
                    class="form-control w-90-per"
                    placeholder="10"
                    v-model="hpaData.hpaMaxReplicas" />
                </div>

                <!-- CPU -->
                <div>
                  <label class="form-check-label mb-2">
                    CPU (%)
                  </label>
                  <input
                    type="number"
                    class="form-control w-80-per d-inline"
                    placeholder="60"
                    v-model="hpaData.hpaCpuUtilization" /> %
                </div>

                <!-- Memory -->
                <div>
                  <label class="form-check-label mb-2">
                    MEMORY (%)
                  </label>
                  <input
                    type="number"
                    class="form-control w-80-per d-inline"
                    placeholder="80"
                    v-model="hpaData.hpaMemoryUtilization" /> %
                </div>
              </div>
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation'">
              <label class="form-label">Workload Rebalancing</label>

              <div class="mb-2">
                <div class="form-check">
                  <input
                    class="form-check-input"
                    type="checkbox"
                    id="workloadRebalancingEnabled"
                    :disabled="isBuiltInPersistentCatalog"
                    v-model="workloadRebalancingEnabled">
                  <label class="form-check-label" for="workloadRebalancingEnabled">
                    Enable Workload Rebalancing
                  </label>
                </div>
              </div>
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation'">
              <label class="form-label">Ingress Configuration</label>

              <div class="mb-2">
                <div class="form-check">
                  <input
                    class="form-check-input"
                    type="checkbox"
                    id="ingressEnabled"
                    :disabled="isBuiltInPersistentCatalog"
                    v-model="ingressData.ingressEnabled">
                  <label class="form-check-label" for="ingressEnabled">
                    Enable Ingress
                  </label>
                  <p v-if="isBuiltInPersistentCatalog" class="text-muted">This TCP application uses an internal ClusterIP Service, not HTTP Ingress. Use an authenticated port-forward for access from your PC.</p>
                </div>
              </div>

              <div v-if="ingressData.ingressEnabled">
                <div class="mb-2">
                  <label class="form-label">Host</label>
                  <input
                    type="text"
                    class="form-control"
                    placeholder="example.com"
                    v-model="ingressData.ingressHost">
                  <p v-if="isIbmCluster" class="text-muted mb-1">For local testing, map this hostname to the IBM load balancer IP in your hosts file. HTTP does not require a certificate and is not encrypted.</p>
                </div>

                <div class="mb-2">
                  <label class="form-label">Path</label>
                  <input
                    type="text"
                    class="form-control"
                    placeholder="/"
                    v-model="ingressData.ingressPath">
                </div>

                <div class="mb-2">
                  <label class="form-label">Ingress Class</label>
                  <input
                    type="text"
                    class="form-control"
                    placeholder="nginx"
                    :value="effectiveIngressClass"
                    disabled>
                </div>

              </div>
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation' && showObjectStorageConfig && isJupyterObjectStorageCatalog">
              <label class="form-label">Object Storage Configuration</label>
              <p class="text-muted">
                Select Object Storage resources already registered in Tumblebug. CSP access keys are not sent to JupyterLab.
              </p>

              <div class="form-check mb-2">
                <input class="form-check-input" type="checkbox" id="k8sJupyterObjectStorageEnabled"
                  v-model="objectStorageData.enabled" :disabled="objectStorageRequired">
                <label class="form-check-label" for="k8sJupyterObjectStorageEnabled">Enable Object Storage</label>
              </div>

              <div v-if="objectStorageData.enabled">
                <div class="d-flex align-items-center justify-content-between mb-2">
                  <label class="form-label mb-0 required">Registered Object Storage</label>
                  <button type="button" class="btn btn-sm btn-outline-secondary"
                    :disabled="registeredObjectStorageLoading" @click="fetchRegisteredObjectStorages">
                    Refresh
                  </button>
                </div>

                <div class="text-muted" v-if="registeredObjectStorageLoading">Loading Object Storage resources...</div>
                <div class="alert alert-danger py-2" v-else-if="registeredObjectStorageLoadError">
                  Object Storage resources could not be loaded from Tumblebug.
                </div>
                <div class="alert alert-warning py-2" v-else-if="registeredObjectStorageList.length === 0">
                  No Object Storage resource is registered in this namespace.
                </div>
                <div class="border rounded p-2" v-else>
                  <div class="form-check" v-for="storage in registeredObjectStorageList" :key="storage.id">
                    <input class="form-check-input" type="checkbox"
                      :id="`vm-object-storage-${storage.id}`"
                      :value="storage.id"
                      :disabled="String(storage.status || '').toLowerCase() !== 'available'"
                      v-model="objectStorageData.selectedStorageIds">
                    <label class="form-check-label" :for="`vm-object-storage-${storage.id}`">
                      {{ storage.name || storage.id }}
                      <span class="text-muted">
                        ({{ storage.provider || 'unknown CSP' }}{{ storage.region ? ` / ${storage.region}` : '' }} / {{ storage.status || 'unknown' }})
                      </span>
                    </label>
                  </div>
                </div>

                <div class="d-flex justify-content-between mt-2">
                  <div class="w-50 me-2">
                    <label class="form-label">Allowed Object Prefix</label>
                    <input type="text" class="form-control" placeholder="Optional: data/project-a/" v-model.trim="objectStorageData.prefix">
                  </div>
                  <div class="w-50 ms-2">
                    <label class="form-label">Access Mode</label>
                    <select class="form-select" v-model="objectStorageData.accessMode">
                      <option value="READ_ONLY">Read only</option>
                      <option value="READ_WRITE">Read and write</option>
                    </select>
                  </div>
                </div>

                <div class="mt-2">
                  <label class="form-label required">Jupyter Access Token</label>
                  <input type="password" class="form-control" placeholder="At least 12 characters" v-model="objectStorageData.jupyterToken" autocomplete="new-password">
                  <p class="text-muted mt-1 mb-0">
                    This is the Jupyter login token, not a CSP secret key. Use it at the Ingress URL shown below.
                  </p>
                </div>

                <p class="text-muted mt-2 mb-0">
                  Jupyter requests short-lived presigned URLs through Application Manager. Multiple CSPs can be selected.
                </p>

                <div class="alert mt-3" :class="objectStorageCheckResult.success ? 'alert-success' : 'alert-danger'" v-if="objectStorageCheckResult">
                  <div>{{ objectStorageCheckResult.success ? 'Object Storage: SUCCESS' : 'Object Storage: FAILED' }}</div>
                  <ul class="mb-0 ps-3">
                    <li v-for="check in objectStorageCheckResult.checks" :key="check.name">
                      {{ check.name }} - {{ check.success ? 'OK' : 'FAIL' }}
                    </li>
                  </ul>
                </div>
              </div>
            </div>

            <div class="mb-3" v-if="modalTitle == 'Application Installation' && showObjectStorageConfig && !isJupyterObjectStorageCatalog">
              <label class="form-label">Object Storage Configuration</label>

              <div class="mb-2">
                <div class="form-check">
                  <input
                    class="form-check-input"
                    type="checkbox"
                    id="objectStorageEnabled"
                    v-model="objectStorageData.enabled"
                    :disabled="objectStorageRequired">
                  <label class="form-check-label" for="objectStorageEnabled">
                    Enable Object Storage
                  </label>
                </div>
              </div>

              <div v-if="objectStorageData.enabled">
                <div class="d-flex justify-content-between">
                  <div class="w-50 me-2">
                    <label class="form-label">Target CSP</label>
                    <input type="text" class="form-control" :value="selectedTargetProvider || '-'" disabled>
                  </div>
                  <div class="w-50 ms-2">
                    <label class="form-label">Storage API</label>
                    <input type="text" class="form-control" value="S3-compatible" disabled>
                  </div>
                </div>

                <div class="mt-2 mb-2">
                  <label class="form-label">S3-compatible Endpoint</label>
                  <input
                    type="text"
                    class="form-control"
                    :placeholder="objectStorageEndpointPlaceholder"
                    v-model="objectStorageData.endpoint">
                </div>

                <div class="d-flex justify-content-between">
                  <div class="w-50 me-2">
                    <label class="form-label">Region</label>
                    <input type="text" class="form-control" :placeholder="objectStorageRegionPlaceholder" v-model="objectStorageData.region">
                  </div>
                  <div class="w-50 ms-2">
                    <label class="form-label">Bucket Name</label>
                    <input type="text" class="form-control" placeholder="object-storage-bucket" v-model="objectStorageData.bucket">
                  </div>
                </div>

                <div class="d-flex justify-content-between mt-2">
                  <div class="w-50 me-2">
                    <label class="form-label">Access Key ID</label>
                    <input type="password" class="form-control" placeholder="access key id" v-model="objectStorageData.accessKey" autocomplete="off">
                  </div>
                  <div class="w-50 ms-2">
                    <label class="form-label">Secret Access Key</label>
                    <input type="password" class="form-control" placeholder="secret access key" v-model="objectStorageData.secretKey" autocomplete="off">
                  </div>
                </div>

                <div class="d-flex gap-4 mt-3">
                  <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="forcePathStyle" v-model="objectStorageData.forcePathStyle">
                    <label class="form-check-label" for="forcePathStyle" title="On: endpoint/bucket/object. Off: bucket.endpoint/object.">Use path-style URL</label>
                  </div>
                </div>

                <div class="alert mt-3" :class="objectStorageCheckResult.success ? 'alert-success' : 'alert-danger'" v-if="objectStorageCheckResult">
                  <div>{{ objectStorageCheckResult.success ? 'Object Storage: SUCCESS' : 'Object Storage: FAILED' }}</div>
                  <ul class="mb-0 ps-3">
                    <li v-for="check in objectStorageCheckResult.checks" :key="check.name">
                      {{ check.name }} - {{ check.success ? 'OK' : 'FAIL' }}
                    </li>
                  </ul>
                </div>
              </div>
            </div>
          </template>
          </fieldset>
        </div>

        <!-- Footer -->
        <div v-if="jupyterInstallationUnsupported && !deploymentCompleted" class="px-3" role="status">
          <p class="alert alert-warning">Jupyter installation is unavailable for KT because AM does not support KT Object Storage integration.</p>
        </div>
        <div v-if="!deploymentCompleted && (specCheckErrors.length || specCheckWarnings.length)" class="px-3" aria-live="polite">
          <div v-for="message in specCheckErrors" :key="message" class="alert alert-danger" role="alert">{{ message }}</div>
          <div v-for="message in specCheckWarnings" :key="message" class="alert alert-warning">{{ message }}</div>
        </div>
        <div
          class="modal-footer d-flex justify-content-between">
          <a
            class="btn btn-link link-secondary"
            :data-bs-dismiss="embedded ? undefined : 'modal'"
            @click="handleCancel">
            {{ deploymentCompleted ? 'Close' : 'Cancel' }}
          </a>

          <button v-if="deploymentCompleted" type="button" class="btn btn-primary" @click="viewAppsStatus">
            View Apps Status
          </button>
          <div v-else>
            <button
              v-if="modalTitle == 'Application Installation' && shouldRunObjectStorageCheck"
              class="btn btn-outline-danger ms-auto me-1"
              @click="runObjectStorageCheck()"
              :disabled="deploying || objectStorageChecking || objectStorageCheckPassed"
              title="Writes, reads, and deletes a temporary object in the selected bucket.">
              {{ objectStorageChecking ? 'Checking...' : 'Storage Check' }}
            </button>
            <button
              v-if="modalTitle == 'Application Installation'"
              class="btn btn-danger ms-auto me-1"
              @click="specCheck"
              :disabled="deploying || specChecking || !specCheckFlag || Boolean(projectScopeError)">
              {{ specChecking ? 'Checking...' : 'Spec Check' }}
            </button>
            <button
              class="btn btn-primary ms-auto"
              @click="runInstall"
              :disabled="deployDisabled">
              {{ deploying ? 'Deploying…' : 'Deploy' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { catalogDisplayName } from '../catalogGrouping'
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
import { onMounted, onBeforeUnmount, watch, computed } from 'vue';
import { useRouter } from 'vue-router';
import { Modal } from 'bootstrap';
// @ts-ignore
import _ from 'lodash';
import { getNsInfo, getMciInfo, getVmInfo, getClusterInfo } from '@/api/tumblebug'
import { getNhnStorageCapability, createNhnStorageClass, getK8sStorageClasses, getRegisteredObjectStorages, getSoftwareCatalogList, k8sSpecCheck, k8sIngressCheck, objectStorageSmokeCheck, runK8SInstall, runAction, runVmInstall, vmSpecCheck } from '@/api/softwareCatalog'
import { type SoftwareCatalog } from '@/views/type/type'
import { useUserStore } from '@/stores/user'
import { isVmClusteringCatalog } from '@/utils/vmClustering'
import { resolveInstallVmTarget } from '@/integration/installTarget'
import { startIngressPreparation, getIngressPreparation, closeInterruptedDeployment } from '@/api/softwareCatalog'
import { waitForIngressPreparation } from '@/utils/ingressPreparation'

interface Props {
  nsId?: string
  title?: string
  embedded?: boolean
  formId?: string
  targetType?: '' | 'VM' | 'K8S'
  targetMciId?: string
  targetVmId?: string
  targetNodeGroupId?: string
  targetClusterId?: string
}

interface VmNodeGroupOption {
  id: string
  totalVmCount: number
  runningVmIds: string[]
}

const toast = useToast()
const userStore = useUserStore()
const router = useRouter()

const props = withDefaults(defineProps<Props>(), {
  nsId: '',
  title: 'Application Installation',
  embedded: false,
  formId: 'install-form',
  targetType: '',
  targetMciId: '',
  targetVmId: '',
  targetNodeGroupId: '',
  targetClusterId: ''
})
const emit = defineEmits<{
  (event: 'ready', payload: Record<string, unknown>): void
  (event: 'deployment-event', payload: Record<string, unknown>): void
  (event: 'cancel'): void
}>()
const modalTitle = computed(() => props.title);
const normalizedTargetType = computed(() => String(props.targetType || '').toUpperCase())
const isTargetLocked = computed(() => props.embedded && ['VM', 'K8S'].includes(normalizedTargetType.value))
const isLockedVmNodeGroupTarget = computed(() => isTargetLocked.value
  && normalizedTargetType.value === 'VM'
  && Boolean(String(props.targetNodeGroupId || '').trim())
  && !String(props.targetVmId || '').trim())
const lockedTargetLabel = computed(() => {
  if (normalizedTargetType.value !== 'VM') {
    return `Kubernetes cluster ${props.targetClusterId}`
  }
  if (isLockedVmNodeGroupTarget.value) {
    return `NodeGroup ${props.targetNodeGroupId} in ${props.targetMciId}`
  }
  const nodeGroup = String(props.targetNodeGroupId || '').trim()
  return nodeGroup
    ? `VM ${props.targetVmId} in NodeGroup ${nodeGroup} / ${props.targetMciId}`
    : `VM ${props.targetVmId} in ${props.targetMciId}`
})

const normalizeScopeValues = (value: unknown): string[] => {
  const values = Array.isArray(value) ? value : [value]

  return values
    .flatMap((item) => typeof item === 'string' ? item.split(',') : [item])
    .map((item: any) => String(item?.id || item?.name || item || '').trim())
    .filter(Boolean)
}

const firstScopeValue = (...values: unknown[]) => {
  for (const value of values) {
    const normalized = normalizeScopeValues(value)
    if (normalized.length > 0) return normalized[0]
  }
  return ''
}

const projectInfo = computed(() => userStore.projectInfo || {})
const workspaceInfo = computed(() => userStore.workspaceInfo || {})
const projectNsId = computed(() => firstScopeValue(
  projectInfo.value.ns_id,
  projectInfo.value.nsId
))
const projectMciIds = computed(() => normalizeScopeValues(
  projectInfo.value.mci_ids
    ?? projectInfo.value.mciIds
    ?? projectInfo.value.mci_id
    ?? projectInfo.value.mciId
))
const projectClusterIds = computed(() => normalizeScopeValues(
  projectInfo.value.cluster_ids
    ?? projectInfo.value.clusterIds
    ?? projectInfo.value.cluster_id
    ?? projectInfo.value.clusterId
))
const hasProjectContext = computed(() => Boolean(
  firstScopeValue(projectInfo.value.id, projectInfo.value.name, projectNsId.value)
))
const isNamespaceLocked = computed(() => hasProjectContext.value && Boolean(projectNsId.value))
const projectContextLabel = computed(() => {
  const workspace = firstScopeValue(workspaceInfo.value.name, workspaceInfo.value.id)
  const project = firstScopeValue(projectInfo.value.name, projectInfo.value.id)
  return [workspace, project].filter(Boolean).join(' / ') || 'the selected project'
})
const projectContextKey = computed(() => JSON.stringify([
  firstScopeValue(workspaceInfo.value.id, workspaceInfo.value.name),
  firstScopeValue(projectInfo.value.id, projectInfo.value.name),
  projectNsId.value,
  projectMciIds.value,
  projectClusterIds.value
]))

const infraList = ref([] as any)
const nsIdList = ref([] as any)
const mciList = ref([] as any)
const vmList = ref([] as any)
const originalVmList = ref([] as any)
const catalogList = ref([] as Array<SoftwareCatalog>)

const selectInfra = ref("" as string)
const selectNsId = ref("" as string)
const selectMci = ref("" as string)
const selectVm = ref("" as string)
const selectedVmList = ref([] as Array<string>)
const vmTargetMode = ref<'VM' | 'NODE_GROUP'>('VM')
const selectVmNodeGroupId = ref('')
const selectDeploymentType = ref("Standalone" as string)
const hpaData = ref({} as any)
const workloadRebalancingEnabled = ref(false)
const ingressData = ref({} as any)
const objectStorageData = ref({} as any)
const objectStorageCheckResult = ref(null as any)
const objectStorageChecking = ref(false as boolean)
const registeredObjectStorageList = ref([] as any[])
const registeredObjectStorageLoading = ref(false as boolean)
const registeredObjectStorageLoadError = ref(false as boolean)
const interruptedDeployment = ref<{ id: string, namespace: string } | null>(null)
const closingInterruptedTracking = ref(false)
const closeInterruptedTracking = async () => {
  const operation = interruptedDeployment.value
  if (!operation || !confirm('Check Apps Status and confirm that no installation is still running before closing this interrupted tracking record. This does not cancel or uninstall an application. Continue?')) return
  closingInterruptedTracking.value = true
  try {
    await closeInterruptedDeployment(operation.id, operation.namespace)
    interruptedDeployment.value = null
    toast.warning('Interrupted tracking closed. The application itself was not changed.')
  } catch { toast.error('Could not close interrupted tracking. Reload its status before retrying.') }
  finally { closingInterruptedTracking.value = false }
}
const deploying = ref(false)
const deploymentCompleted = ref(false)
let preparationEpoch = 0
let installationFormMounted = true
// Bootstrap backdrop/Escape closes can bypass the Cancel button.
const onInstallationModalHide = () => { preparationEpoch++ }
onBeforeUnmount(() => {
  installationFormMounted = false
  document.getElementById(props.formId)?.removeEventListener('hide.bs.modal', onInstallationModalHide)
})
const selectedResourceType = ref("GENERAL_PURPOSE" as string)
const storageClassList = ref([] as any[])
const selectedStorageClass = ref("" as string)
const storageClassLoading = ref(false as boolean)
const storageClassLoadError = ref(false as boolean)
const storageClassFailure = ref('')
const storageCapability = ref<any>(null)
const storageSetupError = ref('')
const storageCreating = ref(false)
const newStorageClassName = ref('mcmp-nhn-notebooks')
const newStorageDiskType = ref('General HDD')
const notebookStorageGi = ref(10)
let storageRequestSequence = 0
const storageErrorDetail = (error: any) => String(error?.response?.data?.detail || error?.message || 'Storage API request failed. Check cluster connectivity and permissions.')
const selectedStorageMinimum = computed(() => Number(storageClassList.value.find(s => s.name === selectedStorageClass.value)?.minimumSizeGi || 1))
watch(selectedStorageClass, () => { notebookStorageGi.value = Math.max(notebookStorageGi.value, selectedStorageMinimum.value) })
watch(notebookStorageGi, () => { onChangeForm() })

const clusterList = ref([] as any)
const selectCluster = ref("" as string)
const inputApplications = ref("" as string)
const inputServicePort = ref("" as string)
const vmNetworkExposureMode = ref<'PRIVATE' | 'RESTRICTED'>('PRIVATE')
const servicePortCidr = ref("" as string)
const k8sOpenIngress = ref(true)
const specCheckFlag = ref(true as boolean)
const specChecking = ref(false)
const specCheckErrors = ref<string[]>([])
const specCheckWarnings = ref<string[]>([])
let specCheckVersion = 0
const selectedCatalogIdx = ref(0 as number)
const projectScopeError = ref('')
let resourceLoadSequence = 0

const getNamespaceValue = (namespace: any) => namespace?.id || namespace?.name || ''
const getMciValue = (mci: any) => mci?.id || mci?.name || ''
const getVmValue = (vm: any) => vm?.id || vm?.name || ''
const getClusterValue = (cluster: any) => cluster?.name || cluster?.id || ''
const isRunningVm = (vm: any) => String(vm?.status || '').trim().toUpperCase() === 'RUNNING'

const vmNodeGroupOptions = computed<VmNodeGroupOption[]>(() => {
  const groups = new Map<string, VmNodeGroupOption>()

  for (const vm of originalVmList.value) {
    const nodeGroupId = String(vm?.subGroupId || '').trim()
    if (!nodeGroupId) continue

    const group = groups.get(nodeGroupId) || {
      id: nodeGroupId,
      totalVmCount: 0,
      runningVmIds: []
    }
    group.totalVmCount += 1

    const vmId = getVmValue(vm)
    if (isRunningVm(vm) && vmId && !group.runningVmIds.includes(vmId)) {
      group.runningVmIds.push(vmId)
    }
    groups.set(nodeGroupId, group)
  }

  return Array.from(groups.values()).sort((left, right) => left.id.localeCompare(right.id))
})

const selectedNodeGroupVmIds = computed(() =>
  vmNodeGroupOptions.value.find((nodeGroup) => nodeGroup.id === selectVmNodeGroupId.value)?.runningVmIds || []
)

const matchesScope = (resource: any, allowedIds: string[]) => {
  if (allowedIds.length === 0) return true

  const resourceIds = [resource?.id, resource?.name, resource?.uid]
    .map((value) => String(value || '').trim().toLowerCase())
    .filter(Boolean)
  const normalizedAllowedIds = allowedIds.map((value) => value.toLowerCase())
  return resourceIds.some((value) => normalizedAllowedIds.includes(value))
}

const clearTargetResources = () => {
  nsIdList.value = []
  mciList.value = []
  vmList.value = []
  originalVmList.value = []
  clusterList.value = []
  registeredObjectStorageList.value = []
  registeredObjectStorageLoadError.value = false
  selectNsId.value = ''
  selectMci.value = ''
  selectVm.value = ''
  selectedVmList.value = []
  selectVmNodeGroupId.value = ''
  selectCluster.value = ''
  projectScopeError.value = ''
}

// watch(modalTitle, async () => {
//   await setInit();
// });

// Handle target infrastructure changes
// Synchronous invalidation also prevents a late response from validating changed form values.
watch([selectInfra, selectNsId, selectCluster, selectMci, selectedVmList, vmTargetMode,
  selectVmNodeGroupId, selectedCatalogIdx, selectedStorageClass, ingressData, servicePortCidr, projectContextKey, modalTitle], () => {
  onChangeForm()
}, { deep: true, flush: 'sync' })

watch(selectInfra, async (newValue) => {
  if (_.isEmpty(selectNsId.value)) return;

  if (newValue === 'VM') {
    // Reset VM related data
    vmTargetMode.value = 'VM'
    selectMci.value = "";
    selectVm.value = "";
    selectedVmList.value = [];
    selectVmNodeGroupId.value = '';
    vmList.value = [];
    originalVmList.value = [];

    // Fetch MCI list
    await _getMciName();
  } else if (newValue === 'K8S') {
    // Reset K8S related data
    selectCluster.value = "";

    // Fetch Cluster list
    await _getClusterName();
  }

  // Reset application selection
  inputApplications.value = "";
  onChangeForm();
});

watch(objectStorageData, () => {
  objectStorageCheckResult.value = null
}, { deep: true })

watch(selectedStorageClass, () => {
  onChangeForm()
})

watch(projectContextKey, async (newContext, previousContext) => {
  if (newContext === previousContext) return

  resourceLoadSequence += 1
  clearTargetResources()
  inputApplications.value = ''
  selectedCatalogIdx.value = 0
  setSpecCheckFlag()

  const modalElement = document.getElementById(props.formId)
  if (props.embedded || modalElement?.classList.contains('show')) {
    await setInit()
  }
})

// Handle deployment type changes
watch(selectDeploymentType, () => {
  if (vmTargetMode.value === 'NODE_GROUP') {
    selectDeploymentType.value = 'Standalone'
    return
  }

  if (selectDeploymentType.value === "Standalone") {
    // Reset selected VMs when changing to Standalone mode
    selectedVmList.value = [];
    // Restore vmList from originalVmList
    vmList.value = [...originalVmList.value];
  } else if (selectDeploymentType.value === "Clustering") {
    // Reset selected VMs when changing to Clustering mode
    selectedVmList.value = [];
    // Restore vmList from originalVmList
    vmList.value = [...originalVmList.value];
  }
});

watch(vmTargetMode, (targetMode) => {
  selectVm.value = ''
  selectedVmList.value = []
  selectVmNodeGroupId.value = targetMode === 'NODE_GROUP' && isLockedVmNodeGroupTarget.value
    ? String(props.targetNodeGroupId || '').trim()
    : ''
  vmList.value = [...originalVmList.value]

  if (targetMode === 'NODE_GROUP') {
    selectDeploymentType.value = 'Standalone'
  }
  onChangeForm()
})

onMounted(async () => {
  document.getElementById(props.formId)?.addEventListener('hide.bs.modal', onInstallationModalHide)
  if (props.embedded) {
    try {
      await setInit()
      if (projectScopeError.value) {
        emit('deployment-event', {
          status: 'FORM_ERROR',
          message: projectScopeError.value
        })
        return
      }
      await _getSoftwareCatalogList()
      emit('ready', {
        targetType: normalizedTargetType.value,
        namespace: selectNsId.value
      })
    } catch (error) {
      projectScopeError.value = projectScopeError.value || 'The software catalog could not be loaded.'
      emit('deployment-event', {
        status: 'FORM_ERROR',
        message: 'The installation form could not be initialized.'
      })
    }
    return
  }

  const modalElement: any = document.getElementById(props.formId);
  if (!modalElement) return
  // Open Modal Action
  modalElement.addEventListener('show.bs.modal', async() => {
    await setInit()
    await _getSoftwareCatalogList()
  });
})

const setInit = async () => {
  preparationEpoch++
  deploymentCompleted.value = false
  interruptedDeployment.value = null
  storageRequestSequence++
  const loadSequence = ++resourceLoadSequence
  clearTargetResources()
  selectInfra.value = isTargetLocked.value ? normalizedTargetType.value : "VM"
  vmTargetMode.value = 'VM'
  selectVmNodeGroupId.value = ''
  selectDeploymentType.value = "Standalone"
  hpaData.value = {
    hpaEnabled: false,
    hpaMinReplicas: 1,
    hpaMaxReplicas: 10,
    hpaCpuUtilization: 60,
    hpaMemoryUtilization: 80
  }
  workloadRebalancingEnabled.value = false
  ingressData.value = {
    ingressEnabled: false,
    ingressHost: '',
    ingressPath: '/',
    ingressClass: 'nginx',
    ingressTlsEnabled: false,
    ingressTlsSecret: ''
  }
  objectStorageData.value = getDefaultObjectStorageData()
  objectStorageCheckResult.value = null
  objectStorageChecking.value = false
  registeredObjectStorageList.value = []
  registeredObjectStorageLoading.value = false
  registeredObjectStorageLoadError.value = false
  storageClassList.value = []
  selectedStorageClass.value = ""
  storageClassLoading.value = false
  storageClassLoadError.value = false
  storageClassFailure.value = ''
  storageCapability.value = null
  storageSetupError.value = ''
  storageCreating.value = false
  notebookStorageGi.value = 10
  selectedResourceType.value = "GENERAL_PURPOSE"
  inputServicePort.value = ""
  vmNetworkExposureMode.value = 'PRIVATE'
  servicePortCidr.value = ""
  inputApplications.value = ""
  selectedCatalogIdx.value = 0

  setInfraList()
  setSpecCheckFlag()

  await _getNsId(loadSequence)
}

const normalizeIngressHost = (host: string) => {
  let normalized = (host || '').trim()
  if (!normalized) return normalized

  normalized = normalized.replace(/^[a-zA-Z][a-zA-Z0-9+.-]*:\/\//, '')
  const atIndex = normalized.lastIndexOf('@')
  if (atIndex >= 0) normalized = normalized.slice(atIndex + 1)

  const delimiterIndex = normalized.search(/[/?#]/)
  if (delimiterIndex >= 0) normalized = normalized.slice(0, delimiterIndex)

  const firstColonIndex = normalized.indexOf(':')
  if (firstColonIndex >= 0 && normalized.indexOf(':', firstColonIndex + 1) < 0) {
    normalized = normalized.slice(0, firstColonIndex)
  }

  return normalized.trim().toLowerCase()
}

const _getSoftwareCatalogList = async () => {
  await getSoftwareCatalogList("").then(({ data }) => {
    catalogList.value = data
  })
}

const setInfraList = () => {
  if (isTargetLocked.value) {
    infraList.value = [{
      key: normalizedTargetType.value,
      value: normalizedTargetType.value
    }]
    return
  }

  infraList.value = [
    {
      key: "VM",
      value: "VM"
    },
    {
      key: "k8s",
      value: "K8S"
    }
  ]
}

const setSpecCheckFlag = () => {
  if (modalTitle.value === 'Application Uninstallation')
    specCheckFlag.value = false
  else
    specCheckFlag.value = true
}

const _getNsId = async (loadSequence = resourceLoadSequence) => {
  try {
    const { data } = await getNsInfo()
    if (loadSequence !== resourceLoadSequence) return

    const namespaces = Array.isArray(data) ? data : []
    if (hasProjectContext.value) {
      if (_.isEmpty(projectNsId.value)) {
        projectScopeError.value = `Project "${projectContextLabel.value}" has no namespace mapping.`
        return
      }

      const scopedNamespace = namespaces.find((namespace: any) =>
        matchesScope(namespace, [projectNsId.value])
      )
      if (!scopedNamespace) {
        projectScopeError.value = `Namespace "${projectNsId.value}" assigned to this project was not found.`
        return
      }

      nsIdList.value = [scopedNamespace]
      selectNsId.value = getNamespaceValue(scopedNamespace)
    } else {
      nsIdList.value = namespaces
      const preferredNsId = firstScopeValue(props.nsId)
      const preferredNamespace = preferredNsId
        ? namespaces.find((namespace: any) => matchesScope(namespace, [preferredNsId]))
        : undefined
      const selectedNamespace = preferredNamespace || namespaces[0]
      selectNsId.value = selectedNamespace ? getNamespaceValue(selectedNamespace) : ''
    }

    if (!_.isEmpty(selectNsId.value)) {
      if (selectInfra.value === 'VM') await _getMciName(loadSequence)
      else if (selectInfra.value === 'K8S') await _getClusterName(loadSequence)
    }
  } catch (error) {
    if (loadSequence !== resourceLoadSequence) return
    projectScopeError.value = 'Namespaces could not be loaded for the selected project.'
  }
}

const _getMciName = async (loadSequence = resourceLoadSequence) => {
  projectScopeError.value = ''
  try {
    const { data } = await getMciInfo(selectNsId.value)
    if (loadSequence !== resourceLoadSequence) return

    const allMcis = Array.isArray(data) ? data : []
    const scopedMcis = allMcis.filter((mci: any) => matchesScope(mci, projectMciIds.value))
    if (isTargetLocked.value && normalizedTargetType.value === 'VM') {
      const targetMci = scopedMcis.find((mci: any) => matchesScope(mci, [props.targetMciId]))
      if (!targetMci) {
        mciList.value = []
        selectMci.value = ''
        projectScopeError.value = `Infra "${props.targetMciId}" was not found in the selected project namespace.`
        return
      }
      mciList.value = [targetMci]
      selectMci.value = getMciValue(targetMci)
      await _getVmName(loadSequence)
      return
    }

    mciList.value = scopedMcis
    if (mciList.value.length > 0) {
      selectMci.value = getMciValue(mciList.value[0])
      await _getVmName(loadSequence)
    } else {
      selectMci.value = ''
      projectScopeError.value = projectMciIds.value.length > 0
        ? `Infra "${projectMciIds.value.join(', ')}" assigned to this project was not found.`
        : 'No VM infrastructure is available in this project.'
    }
  } catch (error) {
    if (loadSequence !== resourceLoadSequence) return
    projectScopeError.value = 'VM infrastructure could not be loaded for the selected project.'
  }
}

const _getVmName = async (loadSequence = resourceLoadSequence) => {
  const params = {
    nsId: selectNsId.value,
    mciId: selectMci.value
  }
  try {
    const { data } = await getVmInfo(params)
    if (loadSequence !== resourceLoadSequence) return

    const availableVms = Array.isArray(data?.node) ? data.node : []
    if (isTargetLocked.value && normalizedTargetType.value === 'VM') {
      const resolvedTarget = resolveInstallVmTarget(availableVms, {
        vmId: props.targetVmId,
        nodeGroupId: props.targetNodeGroupId
      })
      if (!resolvedTarget.ok) {
        originalVmList.value = []
        vmList.value = []
        selectVm.value = ''
        selectedVmList.value = []
        selectVmNodeGroupId.value = ''
        projectScopeError.value = `${resolvedTarget.error} Infra: "${props.targetMciId}".`
        return
      }

      if (resolvedTarget.mode === 'NODE_GROUP') {
        originalVmList.value = resolvedTarget.members
        vmList.value = [...resolvedTarget.members]
        vmTargetMode.value = 'NODE_GROUP'
        selectVmNodeGroupId.value = resolvedTarget.nodeGroupId
        selectDeploymentType.value = 'Standalone'
        selectVm.value = ''
        selectedVmList.value = []
        return
      }

      originalVmList.value = [resolvedTarget.vm]
      vmList.value = [resolvedTarget.vm]
      vmTargetMode.value = 'VM'
      selectVm.value = resolvedTarget.vmId
      selectedVmList.value = [resolvedTarget.vmId]
      return
    }

    originalVmList.value = availableVms
    // Set vmList excluding VMs that are already in selectedVmList
    vmList.value = originalVmList.value.filter((vm: any) =>
      !selectedVmList.value.includes(vm.id)
    )
    selectVm.value = ''
    if (!vmNodeGroupOptions.value.some((nodeGroup) => nodeGroup.id === selectVmNodeGroupId.value)) {
      selectVmNodeGroupId.value = ''
    }
    if (vmList.value.length === 0) {
      projectScopeError.value = 'No VM is available in the infrastructure assigned to this project.'
    }
  } catch (error) {
    if (loadSequence !== resourceLoadSequence) return
    projectScopeError.value = 'VMs could not be loaded for the selected project.'
  }
}

const _getClusterName = async (loadSequence = resourceLoadSequence) => {
  projectScopeError.value = ''
  try {
    const { data } = await getClusterInfo(selectNsId.value)
    if (loadSequence !== resourceLoadSequence) return

    const allClusters = Array.isArray(data) ? data : []
    const scopedClusters = allClusters.filter((cluster: any) => matchesScope(cluster, projectClusterIds.value))
    if (isTargetLocked.value && normalizedTargetType.value === 'K8S') {
      const targetCluster = scopedClusters.find((cluster: any) => matchesScope(cluster, [props.targetClusterId]))
      if (!targetCluster) {
        clusterList.value = []
        selectCluster.value = ''
        projectScopeError.value = `Cluster "${props.targetClusterId}" was not found in the selected project namespace.`
        return
      }
      clusterList.value = [targetCluster]
      selectCluster.value = getClusterValue(targetCluster)
    } else {
      clusterList.value = scopedClusters
      if (clusterList.value.length > 0) {
        selectCluster.value = getClusterValue(clusterList.value[0])
      } else {
        selectCluster.value = ''
        projectScopeError.value = projectClusterIds.value.length > 0
          ? `Cluster "${projectClusterIds.value.join(', ')}" assigned to this project was not found.`
          : 'No Kubernetes cluster is available in this project.'
      }
    }
    if (selectInfra.value === 'K8S' && isJupyterObjectStorageCatalog.value) {
      ingressData.value.ingressEnabled = true
      hpaData.value.hpaEnabled = false
      hpaData.value.hpaMinReplicas = 1
    }
    objectStorageData.value = getDefaultObjectStorageData()
    objectStorageCheckResult.value = null
    await fetchStorageClasses()
  } catch (error) {
    if (loadSequence !== resourceLoadSequence) return
    projectScopeError.value = 'Kubernetes clusters could not be loaded for the selected project.'
  }
}

const fetchRegisteredObjectStorages = async () => {
  registeredObjectStorageList.value = []
  registeredObjectStorageLoadError.value = false

  if (
    !['VM', 'K8S'].includes(selectInfra.value)
    || !isJupyterObjectStorageCatalog.value
    || _.isEmpty(selectNsId.value)
  ) {
    return
  }

  registeredObjectStorageLoading.value = true
  try {
    const { data } = await getRegisteredObjectStorages(selectNsId.value)
    registeredObjectStorageList.value = Array.isArray(data) ? data : []
    const availableIds = registeredObjectStorageList.value
      .filter((storage: any) => String(storage.status || '').toLowerCase() === 'available')
      .map((storage: any) => storage.id)
    objectStorageData.value.selectedStorageIds = (objectStorageData.value.selectedStorageIds || [])
      .filter((id: string) => availableIds.includes(id))
    if (objectStorageData.value.selectedStorageIds.length === 0 && availableIds.length === 1) {
      objectStorageData.value.selectedStorageIds = [availableIds[0]]
    }
  } catch (error) {
    registeredObjectStorageLoadError.value = true
  } finally {
    registeredObjectStorageLoading.value = false
  }
}

const fetchStorageClasses = async (preferred = '') => {
  const sequence = ++storageRequestSequence
  storageClassList.value = []
  selectedStorageClass.value = ''
  storageClassLoadError.value = false
  storageClassFailure.value = ''
  storageCapability.value = null
  storageSetupError.value = ''
  storageClassLoading.value = false
  if (selectInfra.value !== 'K8S' || !supportsStorageClassConfig.value || !selectNsId.value || !selectCluster.value) return
  const target = { namespace: selectNsId.value, clusterName: selectCluster.value }
  storageClassLoading.value = true
  try {
    const { data } = await getK8sStorageClasses(target)
    if (sequence !== storageRequestSequence) return
    if (!Array.isArray(data)) throw new Error('Invalid StorageClass API response. Retry the lookup.')
    storageClassList.value = [...data].sort((a, b) => Number(isRecommendedStorageClass(b)) - Number(isRecommendedStorageClass(a)))
    selectedStorageClass.value = data.some(s => s.name === preferred) ? preferred : getInitialStorageClass(data)
    if (!preferred && selectedClusterProvider.value === 'alibaba') {
      notebookStorageGi.value = Math.max(notebookStorageGi.value, 20)
    }
  } catch (error) {
    if (sequence !== storageRequestSequence) return
    storageClassLoadError.value = true
    storageClassFailure.value = storageErrorDetail(error)
  } finally {
    if (sequence === storageRequestSequence) storageClassLoading.value = false
  }
  if (sequence !== storageRequestSequence || storageClassLoadError.value || storageClassList.value.length || !isNhnCluster.value) return
  try {
    const { data } = await getNhnStorageCapability(target)
    if (sequence === storageRequestSequence) storageCapability.value = data
  } catch (error) {
    if (sequence === storageRequestSequence) storageSetupError.value = storageErrorDetail(error)
  }
}

const createNotebookStorageClass = async () => {
  if (storageCreating.value || !storageCapability.value?.canCreate) return
  const sequence = storageRequestSequence
  const target = { namespace: selectNsId.value, clusterName: selectCluster.value }
  storageCreating.value = true
  storageSetupError.value = ''
  try {
    const { data } = await createNhnStorageClass(target, { name: newStorageClassName.value, diskType: newStorageDiskType.value })
    if (sequence === storageRequestSequence) await fetchStorageClasses(data.name)
  } catch (error) {
    if (sequence === storageRequestSequence) storageSetupError.value = storageErrorDetail(error)
  } finally { storageCreating.value = false }
}

const isRecommendedStorageClass = (item: any) =>
  selectedClusterProvider.value === 'alibaba' &&
  item.name === 'alicloud-disk-topology-alltype' &&
  item.provisioner === 'diskplugin.csi.alibabacloud.com'

const getInitialStorageClass = (items: any[]) => {
  const recommendedClass = items.find(isRecommendedStorageClass)
  const defaultClass = items.find((item: any) => item.defaultClass)
  return recommendedClass?.name || defaultClass?.name || items[0]?.name || ""
}

const onChangeNsId = async () => {
  selectedVmList.value = [];
  await _getMciName(resourceLoadSequence);
  await fetchRegisteredObjectStorages();
  onChangeForm();
}

const onChangeMci = async () => {
  selectedVmList.value = [];
  selectVmNodeGroupId.value = '';
  projectScopeError.value = ''
  await _getVmName(resourceLoadSequence);
  onChangeForm();
}

const onSelectNamespace = async () =>{
  await _getClusterName(resourceLoadSequence);
  onChangeForm();
}

const onChangeForm = () => {
  specCheckVersion += 1
  specCheckErrors.value = []
  specCheckWarnings.value = []
  if(modalTitle.value === 'Application Installation')
    specCheckFlag.value = true

  else if(modalTitle.value === 'Application Uninstallation')
    specCheckFlag.value = false
}

const onSelectVm = () => {
  if (selectVm.value === "") return;

  // In Standalone mode, only one VM can be selected
  if (selectDeploymentType.value === "Standalone") {
    selectedVmList.value = [selectVm.value];
  }
  // In Clustering mode, add after checking for duplicates
  else if (selectDeploymentType.value === "Clustering") {
    if (!selectedVmList.value.includes(selectVm.value)) {
      selectedVmList.value.push(selectVm.value);

      // Remove the selected VM from vmList
      const vmIndex = vmList.value.findIndex((vm: any) => vm.id === selectVm.value);
      if (vmIndex !== -1) {
        vmList.value.splice(vmIndex, 1);
      }
    }
  }

  // Reset selection
  selectVm.value = "";
  onChangeForm();
}

const onSelectVmNodeGroup = () => {
  selectedVmList.value = []
  selectDeploymentType.value = 'Standalone'
  onChangeForm()
}

const removeVm = (index: number) => {
  if (isTargetLocked.value) return

  const removedVmId = selectedVmList.value[index];
  selectedVmList.value.splice(index, 1);

  // Add back to vmList only in Clustering mode
  if (selectDeploymentType.value === "Clustering") {
    // Find the removed VM from originalVmList and add it to vmList
    const removedVm = originalVmList.value.find((vm: any) => vm.id === removedVmId);
    if (removedVm) {
      vmList.value.push(removedVm);
    }
  }

  onChangeForm();
}

const handleCancel = async () => {
  preparationEpoch++
  emit('cancel')
  if (deploying.value) return // Shared preparation may finish, but must not trigger an app deployment.
  if (deploymentCompleted.value) return // Keep the completed state until a new form is opened.
  await setInit()
}

const viewAppsStatus = async () => {
  const element = document.getElementById(props.formId)
  const modal = element && !props.embedded ? Modal.getInstance(element) : null
  if (modal && element?.classList.contains('show')) {
    await new Promise<void>(resolve => {
      element.addEventListener('hidden.bs.modal', () => resolve(), { once: true })
      modal.hide()
    })
  }
  await router.push({ name: 'applicationStatus' })
}

const getDeploymentTarget = () => selectInfra.value === 'VM'
  ? vmTargetMode.value === 'NODE_GROUP'
    ? {
        targetType: 'VM',
        namespace: selectNsId.value,
        mciId: selectMci.value,
        nodeGroupId: selectVmNodeGroupId.value
      }
    : {
        targetType: 'VM',
        namespace: selectNsId.value,
        mciId: selectMci.value,
        vmId: selectedVmList.value[0] || '',
        ...(isTargetLocked.value && props.targetNodeGroupId
          ? { nodeGroupId: props.targetNodeGroupId }
          : {})
      }
  : {
      targetType: 'K8S',
      namespace: selectNsId.value,
      clusterId: selectCluster.value
    }

const emitDeploymentEvent = (status: string, detail: Record<string, unknown> = {}) => {
  emit('deployment-event', {
    status,
    target: getDeploymentTarget(),
    ...detail
  })
}

const getDeploymentId = (responseData: any) => {
  if (!responseData || typeof responseData !== 'object') return undefined
  return responseData.deploymentId || responseData.applicationId || responseData.id || undefined
}

const runInstall = async () => {
  if (deploying.value || deploymentCompleted.value) return
  if (jupyterInstallationUnsupported.value) {
    toast.error('Jupyter installation is unavailable for KT because AM does not support KT Object Storage integration.')
    return
  }
  if (modalTitle.value === 'Application Installation' && selectInfra.value === 'VM'
    && selectDeploymentType.value === 'Clustering' && !canSelectClustering.value) {
    toast.error('Clustering is available only for Redis and Elasticsearch on individually selected VMs')
    return
  }
  if (modalTitle.value === 'Application Installation' && (specCheckFlag.value || specChecking.value)) {
    toast.error('Please complete Spec Check for the current settings before deploying')
    return
  }
  if (projectScopeError.value) {
    toast.error(projectScopeError.value)
    return
  }
  if (selectInfra.value !== 'VM' && selectInfra.value !== 'K8S') {
    toast.error('Please Select Infra')
    return
  }
  if (
    selectInfra.value === 'VM'
    && modalTitle.value === 'Application Installation'
    && vmNetworkExposureMode.value === 'RESTRICTED'
    && (!servicePortCidr.value || servicePortCidr.value === '0.0.0.0/0')
  ) {
    toast.error('Enter a restricted IPv4 CIDR such as 203.0.113.10/32')
    return
  }
  if (selectInfra.value === 'K8S' && modalTitle.value === 'Application Installation' && ingressData.value.ingressEnabled) {
    if (!servicePortCidr.value || servicePortCidr.value === '0.0.0.0/0') {
      toast.error('Enter a restricted IPv4 CIDR for external access')
      return
    }
  }
  if (selectInfra.value === 'K8S' && isJupyterObjectStorageCatalog.value) {
    if (!ingressData.value.ingressEnabled || !ingressData.value.ingressHost || !servicePortCidr.value || servicePortCidr.value === '0.0.0.0/0') {
      toast.error('Enter an Ingress hostname and a restricted IPv4 CIDR for Jupyter')
      return
    }
    if (ingressData.value.ingressPath !== '/' || (!isIbmCluster.value && ingressData.value.ingressTlsEnabled) || hpaData.value.hpaEnabled || workloadRebalancingEnabled.value) {
      toast.error(isIbmCluster.value
        ? 'Jupyter uses one replica and path / without HPA or rebalancing'
        : 'Jupyter uses one replica, path / and HTTP NodePort 30880 without HPA or rebalancing')
      return
    }
  }
  if (isJupyterObjectStorageCatalog.value) {
    if ((objectStorageData.value.selectedStorageIds || []).length === 0) {
      toast.error('Select at least one registered Object Storage resource')
      return
    }
    if (String(objectStorageData.value.jupyterToken || '').length < 12) {
      toast.error('Enter a Jupyter access token with at least 12 characters')
      return
    }
    if (!objectStorageCheckPassed.value) {
      toast.error('Run the Object Storage check before deployment')
      return
    }
  }
  if (selectInfra.value === 'K8S' && !validateStorageClassSelection()) return

  deploying.value = true
  const deploymentContext = projectContextKey.value
  const deploymentSpecVersion = specCheckVersion
  const deploymentEpoch = preparationEpoch
  const isCurrentDeployment = () => installationFormMounted && preparationEpoch === deploymentEpoch
    && projectContextKey.value === deploymentContext && specCheckVersion === deploymentSpecVersion
  emitDeploymentEvent('DEPLOY_STARTED')

  try {
    let res = {} as any

    if (selectInfra.value === 'VM') {
      let params = {} as any
      if (modalTitle.value == 'Application Installation') {
        const isNodeGroupDeployment = vmTargetMode.value === 'NODE_GROUP'
        // Generate clusterName (only required in Clustering mode)
        const clusterName = !isNodeGroupDeployment && selectDeploymentType.value === "Clustering"
          ? `${inputApplications.value}-cluster`
          : `${inputApplications.value}-standalone`;
        const servicePort = inputServicePort.value === "" ? undefined : Number(inputServicePort.value);

        params = {
          namespace: selectNsId.value,
          mciId: selectMci.value,
          vmIds: isNodeGroupDeployment ? [] : selectedVmList.value,
          vmNodeGroupId: isNodeGroupDeployment
            ? selectVmNodeGroupId.value
            : (isTargetLocked.value && props.targetNodeGroupId
              ? props.targetNodeGroupId
              : undefined),
          clusterName: clusterName,
          catalogId: selectedCatalogIdx.value,
          servicePort,
          openServicePort: vmNetworkExposureMode.value === 'RESTRICTED',
          servicePortCidr: vmNetworkExposureMode.value === 'RESTRICTED' ? servicePortCidr.value : undefined,
          username: "admin",
          deploymentType: selectInfra.value,
          vmDeploymentMode: isNodeGroupDeployment ? 'STANDALONE' : selectDeploymentType.value.toUpperCase(),
          resourceType: selectedResourceType.value,
          additionalConfig: buildVmAdditionalConfig(),
        }
        res = await runVmInstall(params, isCurrentDeployment)
      } else {
        res = await runAction(params)
      }
    } else {
      const servicePort = inputServicePort.value === "" ? undefined : Number(inputServicePort.value);
      const additionalConfig = buildK8sAdditionalConfig()
      const params = {
        namespace: selectNsId.value,
        clusterName: selectCluster.value,
        openServicePort: ingressData.value.ingressEnabled && !isIbmCluster.value && k8sOpenIngress.value,
        servicePortCidr: ingressData.value.ingressEnabled ? servicePortCidr.value : undefined,
        catalogId: selectedCatalogIdx.value,
        servicePort,
        username: "",
        deploymentType: selectInfra.value,
        hpaEnabled: hpaData.value.hpaEnabled,
        minReplicas: hpaData.value.hpaMinReplicas,
        maxReplicas: hpaData.value.hpaMaxReplicas,
        cpuThreshold: hpaData.value.hpaCpuUtilization,
        memoryThreshold: hpaData.value.hpaMemoryUtilization,
        workloadRebalancingEnabled: workloadRebalancingEnabled.value,
        resourceType: selectedResourceType.value,
        ...buildIngressPayload(),
        additionalConfig
      }

      if (modalTitle.value === 'Application Installation' && isIbmCluster.value && params.ingressEnabled) {
        const { data } = await startIngressPreparation({
          namespace: params.namespace, clusterName: params.clusterName, catalogId: params.catalogId,
          servicePortCidr: params.servicePortCidr, ...buildIngressPayload()
        })
        await waitForIngressPreparation(data, params,
          async id => (await getIngressPreparation(params.namespace, id)).data,
          () => {}, // Keep preparation internal; the form shows only the generic busy state.
          isCurrentDeployment)
      }
      res = modalTitle.value == 'Application Installation'
        ? await runK8SInstall(params, isCurrentDeployment)
        : await runAction(params)
    }

    if (!isCurrentDeployment()) return
    if (res.data) {
      deploymentCompleted.value = true
      toast.success('SUCCESS')
      emitDeploymentEvent('DEPLOY_SUCCEEDED', {
        deploymentId: getDeploymentId(res.data)
      })
    } else {
      toast.error('FAIL')
      emitDeploymentEvent('DEPLOY_FAILED', {
        message: 'The deployment API did not return a success result.'
      })
    }
  } catch (error) {
    if (!isCurrentDeployment()) return
    const message = error instanceof Error ? error.message : 'The deployment request failed.'
    if ((error as any)?.deploymentStatusUnknown) {
      if ((error as any).interruptedOperation) interruptedDeployment.value = (error as any).interruptedOperation
      toast.warning(message)
      emitDeploymentEvent('DEPLOY_STATUS_UNKNOWN', { message })
    } else {
      toast.error(message)
      emitDeploymentEvent('DEPLOY_FAILED', { message })
    }
  } finally {
    deploying.value = false
  }
}

// Use identical effective form inputs for preflight and deployment. Empty UI input means omitted.
const buildIngressPayload = () => ({
  ingressEnabled: ingressData.value.ingressEnabled,
  ingressHost: normalizeIngressHost(ingressData.value.ingressHost),
  ingressPath: ingressData.value.ingressPath,
  ingressClass: effectiveIngressClass.value,
  // The IBM installation UI is HTTP-only, even when the catalog carries TLS defaults.
  ingressTlsEnabled: isIbmCluster.value ? false : ingressData.value.ingressTlsEnabled,
  ingressTlsSecret: isIbmCluster.value ? null : (ingressData.value.ingressTlsSecret === '' ? null : ingressData.value.ingressTlsSecret)
})

const specCheck = async () => {
  if (specChecking.value) return
  if (projectScopeError.value) {
    toast.error(projectScopeError.value)
    return
  }

  if (selectInfra.value !== 'VM' && selectInfra.value !== 'K8S') {
    toast.error("Please Select Infra")
    return
  }
  if (!validateStorageClassSelection()) return

  const version = specCheckVersion
  specCheckFlag.value = true
  specCheckErrors.value = []
  specCheckWarnings.value = []
  specChecking.value = true
  try {
    if (selectInfra.value === 'K8S') {
      if (!selectNsId.value || !selectCluster.value || !selectedCatalogIdx.value) {
        toast.error('Please select all items')
        return
      }
      const { data } = await k8sIngressCheck({
        namespace: selectNsId.value,
        clusterName: selectCluster.value,
        catalogId: selectedCatalogIdx.value,
        servicePortCidr: servicePortCidr.value,
        ...buildIngressPayload()
      })
      if (version !== specCheckVersion) return
      specCheckWarnings.value = Array.isArray(data?.warnings) ? data.warnings : []
      if (data?.valid !== true) {
        specCheckErrors.value = Array.isArray(data?.errors) && data.errors.length
          ? data.errors : ['Ingress check failed. Resolve the issue and retry Spec Check.']
        return // A route conflict must never become the resource-spec "continue anyway" prompt.
      }
    }

    const checkedValue = await specCheckCallback()
    if (version !== specCheckVersion) return
    if (checkedValue == null) {
      toast.error('Please select all items')
      return
    }
    if (checkedValue === false) {
      const infraName = selectInfra.value === 'VM' ? 'VM' : 'CLUSTER'
      if (!confirm('Your selected ' + infraName + ' has lower specifications than recommended. Would you like to continue with the installation?')) return
    }
    if (version !== specCheckVersion) return
    toast.success('Please click Deploy')
    specCheckFlag.value = false
  } catch {
    if (version === specCheckVersion) {
      specCheckErrors.value = ['Spec Check could not be completed. Check connectivity and permissions, then retry.']
    }
  } finally {
    specChecking.value = false
  }
}

const specCheckCallback = async () => {
  let result = false as boolean;

  if (selectInfra.value === 'VM') {
    const targetVmIds = vmTargetMode.value === 'NODE_GROUP'
      ? selectedNodeGroupVmIds.value
      : selectedVmList.value

    if (
      selectNsId.value === "" ||
      selectMci.value === "" ||
      (vmTargetMode.value === 'NODE_GROUP' && selectVmNodeGroupId.value === '') ||
      targetVmIds.length === 0 ||
      selectedCatalogIdx.value === 0) {
      return null;
    }
    else {
      // Every VM in a NodeGroup is checked because a partially undersized group
      // would otherwise fail only after deployment had already started.
      const vmIdsToCheck = vmTargetMode.value === 'NODE_GROUP'
        ? targetVmIds
        : [targetVmIds[0]]

      result = true
      for (const vmId of vmIdsToCheck) {
        const params = {
          namespace: selectNsId.value,
          mciName: selectMci.value,
          vmName: vmId,
          catalogId: selectedCatalogIdx.value
        }

        const { data } = await vmSpecCheck(params)
        if (!data) {
          result = false
          break
        }
      }
    }
  }
  else if (selectInfra.value === 'K8S') {
    if (
      selectNsId.value === "" ||
      selectCluster.value === "" ||
      selectedCatalogIdx.value === 0) {
      return null;
    }
    const params = {
      namespace: selectNsId.value,
      clusterName: selectCluster.value,
      catalogId: selectedCatalogIdx.value
    }
    await k8sSpecCheck(params).then(({ data }) => {
      result = data
    })
  }

  return result;
}

const selectedCatalogInfo = computed(() => {
  return catalogList.value.find((catalog) => catalog.id === selectedCatalogIdx.value)
})

const canSelectClustering = computed(() => selectInfra.value === 'VM'
  && vmTargetMode.value === 'VM'
  && !isTargetLocked.value
  && isVmClusteringCatalog(selectedCatalogInfo.value))

// Never retain a hidden Clustering selection after changing catalog or target.
watch(canSelectClustering, (allowed) => {
  if (!allowed && selectDeploymentType.value === 'Clustering') {
    selectDeploymentType.value = 'Standalone'
  }
}, { flush: 'sync' })

const selectedClusterProvider = computed(() => {
  const cluster = clusterList.value.find((item: any) => item.id === selectCluster.value || item.name === selectCluster.value)
  return String(cluster?.connectionConfig?.providerName || '').trim().toLowerCase()
})

const isIbmCluster = computed(() => /^(ibm|ibmcloud|ibm-cloud|ibm-vpc|ibmvpc)(-|$)/i.test(selectedClusterProvider.value))
const isNhnCluster = computed(() => selectedClusterProvider.value === 'nhn')
const effectiveIngressClass = computed(() => isIbmCluster.value ? 'public-iks-k8s-nginx' : 'nginx')

const selectedVmProvider = computed(() => {
  const selectedVmId = selectedVmList.value[0]
  const vm = originalVmList.value.find((item: any) => getVmValue(item) === selectedVmId)
  return vm?.connectionConfig?.providerName || vm?.connectionName || ''
})

const selectedTargetProvider = computed(() => {
  return selectInfra.value === 'VM' ? selectedVmProvider.value : selectedClusterProvider.value
})

const selectedCatalogChartName = computed(() => {
  return String(selectedCatalogInfo.value?.helmChart?.chartName || '').toLowerCase()
})

const OBJECT_STORAGE_CAPABILITY = 'object-storage'
const STORAGE_CLASS_CAPABILITY = 'storage-class'
const CONFIG_CAPABILITY_REF_TYPES = ['CAPABILITY', 'TAG']

const isLokiCatalog = computed(() => selectedCatalogChartName.value === 'loki')
const isBuiltInPersistentCatalog = computed(() => {
  const chart = selectedCatalogInfo.value?.helmChart
  return selectInfra.value === 'K8S' && chart?.repositoryName === 'mcmp-builtin'
    && chart?.chartRepositoryUrl === 'classpath:helm' && chart?.chartVersion === '0.1.0'
    && chart?.packageId === 'mcmp-builtin-' + selectedCatalogChartName.value
    && ['redis', 'mariadb', 'postgresql'].includes(selectedCatalogChartName.value)
})
function applyBuiltInPersistentDefaults() {
  if (!isBuiltInPersistentCatalog.value) return
  ingressData.value.ingressEnabled = false
  hpaData.value.hpaEnabled = false
  hpaData.value.hpaMinReplicas = 1
  hpaData.value.hpaMaxReplicas = 1
  workloadRebalancingEnabled.value = false
}
watch(isBuiltInPersistentCatalog, applyBuiltInPersistentDefaults)
const isJupyterObjectStorageCatalog = computed(() => {
  const packageName = String(selectedCatalogInfo.value?.packageInfo?.packageName || '').toLowerCase()
  return packageName.includes('jupyter') && hasObjectStorageCapability(selectedCatalogInfo.value as SoftwareCatalog)
})

const jupyterInstallationUnsupported = computed(() => {
  if (modalTitle.value !== 'Application Installation' || !isJupyterObjectStorageCatalog.value) return false
  const isKtProvider = (provider: unknown) => /^kt(?:classic|cloud|-cloud)?(?:[-_]|$)/i.test(String(provider || '').trim())
  if (selectInfra.value === 'K8S') return isKtProvider(selectedClusterProvider.value)
  return originalVmList.value.some((vm: any) => selectedVmList.value.includes(getVmValue(vm))
    && isKtProvider(vm?.connectionConfig?.providerName || vm?.connectionName))
})

const supportsStorageClassConfig = computed(() => {
  if (selectInfra.value !== 'K8S') return false
  if (isJupyterObjectStorageCatalog.value) return true
  if (isBuiltInPersistentCatalog.value) return true
  if (!selectedCatalogInfo.value?.helmChart) return false
  if (!isLokiCatalog.value) return false
  return hasCatalogCapability(selectedCatalogInfo.value, STORAGE_CLASS_CAPABILITY)
})

const storageClassRequired = computed(() => {
  return supportsStorageClassConfig.value && (isLokiCatalog.value || isJupyterObjectStorageCatalog.value || isBuiltInPersistentCatalog.value)
})

const showStorageClassConfig = computed(() => {
  return supportsStorageClassConfig.value
    && modalTitle.value === 'Application Installation'
    && (storageClassRequired.value || storageClassList.value.length > 0 || storageClassLoadError.value)
})

const storageClassSelectDisabled = computed(() => {
  return storageClassLoading.value || storageClassList.value.length <= 1
})

const storageClassPlaceholder = computed(() => {
  if (storageClassLoading.value) return 'Loading StorageClasses...'
  if (storageClassLoadError.value) return 'Failed to load StorageClasses'
  if (storageClassList.value.length === 0) return 'No StorageClass found'
  return 'Select StorageClass'
})

const storageClassErrorMessage = computed(() => {
  if (!storageClassRequired.value) return ''
  if (storageClassLoading.value) return 'StorageClass list is loading.'
  if (storageClassLoadError.value) return storageClassFailure.value || 'StorageClass list could not be loaded.'
  if (storageClassList.value.length === 0) return 'This application requires a StorageClass, but none was found.'
  if (_.isEmpty(selectedStorageClass.value)) return 'This application requires a StorageClass.'
  if (isJupyterObjectStorageCatalog.value && (!Number.isInteger(notebookStorageGi.value) || notebookStorageGi.value < selectedStorageMinimum.value))
    return 'Enter a whole-number notebook capacity of at least ' + selectedStorageMinimum.value + ' GiB.'
  if (isBuiltInPersistentCatalog.value && (!Number.isInteger(notebookStorageGi.value) || notebookStorageGi.value < selectedStorageMinimum.value || notebookStorageGi.value > 9999))
    return 'Enter a whole-number volume capacity between ' + selectedStorageMinimum.value + ' and 9999 GiB.'
  return ''
})

const objectStorageEndpointPlaceholder = computed(() => {
  return isAwsProvider(selectedTargetProvider.value)
    ? 'Optional: https://s3.ap-northeast-2.amazonaws.com'
    : 'https://object-storage.example.com'
})

const objectStorageRegionPlaceholder = computed(() => {
  return isAwsProvider(selectedTargetProvider.value)
    ? 'ap-northeast-2'
    : 'region from object storage service'
})

const showObjectStorageConfig = computed(() => {
  if (!selectedCatalogInfo.value || !hasObjectStorageCapability(selectedCatalogInfo.value)) return false
  if (selectInfra.value === 'VM') {
    return Boolean(selectedCatalogInfo.value.packageInfo) && isJupyterObjectStorageCatalog.value
  }
  if (selectInfra.value === 'K8S') {
    return Boolean(selectedCatalogInfo.value.helmChart) || isJupyterObjectStorageCatalog.value
  }
  return false
})

const shouldRunObjectStorageCheck = computed(() => {
  return showObjectStorageConfig.value && objectStorageData.value.enabled
})

const objectStorageCheckPassed = computed(() => {
  return !shouldRunObjectStorageCheck.value || objectStorageCheckResult.value?.success === true
})

const deployDisabled = computed(() => {
  return deploying.value
    || deploymentCompleted.value
    || jupyterInstallationUnsupported.value
    || specChecking.value
    || Boolean(projectScopeError.value)
    || specCheckFlag.value
    || !objectStorageCheckPassed.value
    || (storageClassRequired.value && !_.isEmpty(storageClassErrorMessage.value))
})

function getDefaultObjectStorageData(provider = selectedTargetProvider.value, enabled = objectStorageRequired.value) {
  const isAws = isAwsProvider(provider)

  return {
    enabled: Boolean(enabled),
    selectedStorageIds: [],
    accessMode: 'READ_ONLY',
    backendType: 's3',
    endpoint: '',
    region: '',
    bucket: '',
    prefix: '',
    accessKey: '',
    secretKey: '',
    sessionToken: '',
    jupyterToken: '',
    forcePathStyle: !isAws
  }
}

function isAwsProvider(provider: string) {
  return String(provider || '').toLowerCase().includes('aws')
}

const objectStorageRequired = computed(() => {
  return (selectInfra.value === 'K8S' && isLokiCatalog.value)
    || isJupyterObjectStorageCatalog.value
})

function hasObjectStorageCapability(catalog: SoftwareCatalog) {
  return hasCatalogCapability(catalog, OBJECT_STORAGE_CAPABILITY)
}

function hasCatalogCapability(catalog: SoftwareCatalog, capability: string) {
  const refs = catalog.catalogRefs || []
  return refs.some((ref: any) => {
    const refType = String(ref.refType || '').toUpperCase()
    const refValue = String(ref.refValue || '').toLowerCase()
    return refValue === capability && CONFIG_CAPABILITY_REF_TYPES.includes(refType)
  })
}

function buildObjectStorageConfig() {
  if (selectInfra.value === 'VM' || isJupyterObjectStorageCatalog.value) {
    const selectedIds = objectStorageData.value.selectedStorageIds || []
    return {
      enabled: objectStorageData.value.enabled,
      jupyterToken: objectStorageData.value.jupyterToken,
      storages: selectedIds.map((objectStorageId: string) => ({
        objectStorageId,
        alias: objectStorageId,
        prefix: objectStorageData.value.prefix,
        accessMode: objectStorageData.value.accessMode
      }))
    }
  }

  return {
    enabled: objectStorageData.value.enabled,
    backendType: objectStorageData.value.backendType,
    endpoint: objectStorageData.value.endpoint,
    region: objectStorageData.value.region,
    bucket: objectStorageData.value.bucket,
    prefix: objectStorageData.value.prefix,
    accessKey: objectStorageData.value.accessKey,
    secretKey: objectStorageData.value.secretKey,
    sessionToken: objectStorageData.value.sessionToken,
    jupyterToken: objectStorageData.value.jupyterToken,
    forcePathStyle: objectStorageData.value.forcePathStyle,
    insecure: isHttpEndpoint(objectStorageData.value.endpoint)
  }
}

function buildVmAdditionalConfig() {
  if (!showObjectStorageConfig.value || !objectStorageData.value.enabled) return undefined
  return { objectStorage: buildObjectStorageConfig() }
}

function buildK8sAdditionalConfig() {
  const config = {} as Record<string, any>
  if (storageClassRequired.value && !_.isEmpty(selectedStorageClass.value)) {
    config.storageClass = selectedStorageClass.value
    if (isJupyterObjectStorageCatalog.value || isBuiltInPersistentCatalog.value) {
      config.storageSize = notebookStorageGi.value + 'Gi'
      config.storageAccessMode = 'ReadWriteOnce'
    }
  }
  if (showObjectStorageConfig.value && objectStorageData.value.enabled) {
    config.objectStorage = buildObjectStorageConfig()
  }
  return Object.keys(config).length > 0 ? config : undefined
}

function validateStorageClassSelection() {
  if (!storageClassRequired.value) return true

  const message = storageClassErrorMessage.value
  if (!_.isEmpty(message)) {
    toast.error(message)
    return false
  }
  return true
}

function isHttpEndpoint(endpoint: string) {
  return String(endpoint || '').trim().toLowerCase().startsWith('http://')
}

const runObjectStorageCheck = async (showToast = true) => {
  if (!shouldRunObjectStorageCheck.value) return true

  objectStorageChecking.value = true
  objectStorageCheckResult.value = null

  const params = {
    targetType: selectInfra.value as 'VM' | 'K8S',
    namespace: selectNsId.value,
    clusterName: selectInfra.value === 'K8S' ? selectCluster.value : undefined,
    mciId: selectInfra.value === 'VM' ? selectMci.value : undefined,
    vmId: selectInfra.value === 'VM' ? selectedVmList.value[0] : undefined,
    catalogId: selectedCatalogIdx.value,
    objectStorage: buildObjectStorageConfig()
  }

  try {
    const { data } = await objectStorageSmokeCheck(params)
    objectStorageCheckResult.value = data

    if (data?.success) {
      if (showToast) toast.success('Object Storage check succeeded')
      return true
    }

    if (showToast) toast.error('Object Storage check failed')
    return false
  } catch (error) {
    if (showToast) toast.error('Object Storage check failed')
    return false
  } finally {
    objectStorageChecking.value = false
  }
}

// Filter catalog list based on selected infrastructure
const filteredCatalogList = computed(() => {
  if (selectInfra.value === 'VM') {
    return catalogList.value.filter(catalog => catalog.packageInfo)
  } else if (selectInfra.value === 'K8S') {
    return catalogList.value.filter(catalog => catalog.helmChart || (String(catalog.packageInfo?.packageName || '').toLowerCase().includes('jupyter') && hasObjectStorageCapability(catalog)))
  }
  return catalogList.value
})

const onChangeCatalog = async () => {
  if(modalTitle.value === 'Application Installation') specCheckFlag.value = true

  const catalogInfo = filteredCatalogList.value.find((catalog) => inputApplications.value === catalog.name)
  if (catalogInfo) {
    selectedCatalogIdx.value = catalogInfo.id
    inputServicePort.value = selectInfra.value === 'K8S' && isJupyterObjectStorageCatalog.value
      ? '8888' : (catalogInfo.defaultPort ? String(catalogInfo.defaultPort) : "")
    hpaData.value = {
      hpaEnabled: Boolean(catalogInfo.hpaEnabled),
      hpaMinReplicas: catalogInfo.minReplicas || 1,
      hpaMaxReplicas: catalogInfo.maxReplicas || 10,
      hpaCpuUtilization: catalogInfo.cpuThreshold || 60,
      hpaMemoryUtilization: catalogInfo.memoryThreshold || 80
    }
    ingressData.value = {
      ingressEnabled: Boolean(catalogInfo.ingressEnabled),
      ingressHost: catalogInfo.ingressHost || '',
      ingressPath: catalogInfo.ingressPath || '/',
      ingressClass: catalogInfo.ingressClass || 'nginx',
      ingressTlsEnabled: Boolean(catalogInfo.ingressTlsEnabled),
      ingressTlsSecret: catalogInfo.ingressTlsSecret || ''
    }
    applyBuiltInPersistentDefaults()
    if (selectInfra.value === 'K8S' && isJupyterObjectStorageCatalog.value) {
      ingressData.value.ingressEnabled = true
      hpaData.value.hpaEnabled = false
      hpaData.value.hpaMinReplicas = 1
    }
    objectStorageData.value = getDefaultObjectStorageData()
    objectStorageCheckResult.value = null
  }

  await fetchRegisteredObjectStorages()
  await fetchStorageClasses()
}

const onChangeCluster = async () => {
  if(modalTitle.value === 'Application Installation') specCheckFlag.value = true
  objectStorageData.value = getDefaultObjectStorageData()
  objectStorageCheckResult.value = null
  await fetchStorageClasses()
}

</script>
<style scoped>
.install-embedded {
  width: 100%;
}
.install-embedded-dialog {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
}
.install-embedded-body {
  overflow-y: visible;
}
.w-80-per {
  width: 80% !important;
}
.w-90-per {
  width: 90% !important;
}
</style>
