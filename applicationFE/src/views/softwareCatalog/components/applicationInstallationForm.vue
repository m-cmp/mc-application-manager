<template>
  <div class="modal fade" id="install-form" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            {{ modalTitle }}
          </h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" @click="setInit"></button>
        </div>
        <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">

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
              @click="onChangeForm">
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
                  id="namesapce" 
                  v-model="selectNsId"
                  @change="onChangeNsId">
                  <option 
                    v-for="ns in nsIdList" 
                    :value=ns.name 
                    :key="ns.name">
                    {{ ns.name }}
                  </option>
                </select>
              </template>
              
              <template v-else>
                <select 
                  class="form-select" 
                  id="namesapce" 
                  v-model="selectNsId" 
                  @change="onChangeNsId">
                  <option value="selectNsId">
                    {{ selectNsId }}
                  </option>
                </select>
              </template>
            </div>

            <!-- VM :: MCI Name -->
            <div class="mb-3">
              <label class="form-label">MCI Name</label>
              <p 
                v-if="modalTitle == 'Application Installation'" 
                class="text-muted">
                Select the multi-cloud infrastructure information where the application will be deployed</p>
              <p 
                v-else-if="modalTitle == 'Application Uninstallation'" 
                class="text-muted">
                Remove the application and associated resources from the multi-cloud infrastructure</p>
              <select 
                class="form-select" 
                id="mci-name" 
                :disabled="selectNsId == ''" 
                v-model="selectMci"
                @change="onChangeMci">
                <option 
                  v-for="mci in mciList" 
                  :value=mci.id 
                  :key="mci.name">
                    {{ mci.name }}
                  </option>
              </select>
            </div>

            <!-- VM :: VM Name -->
            <div class="mb-3">
              <label class="form-label">VM Name</label>
              <p 
                class="text-muted">
                Select the virtual machine (VM) within the chosen multi-cloud infrastructure where the application will be deployed</p>
              <select 
                class="form-select" 
                id="mci-name" 
                :disabled="selectMci == ''" 
                v-model="selectVm"
                @change="onSelectVm">
                <option value="">Select VM</option>
                <option 
                  v-for="vm in vmList" 
                  :value="vm.id" 
                  :key="vm.name">
                  {{ vm.name }}
                </option>
              </select>

              <div class="mt-2" style="display: flex; gap: 10px; flex-wrap: wrap;" v-if="selectedVmList.length > 0">
                <label 
                  v-for="(vmId, index) in selectedVmList" 
                  :key="index"
                  class="form-check-label" 
                  style="border: 1px solid #000; padding: 5px; border-radius: 5px; cursor: pointer;">
                  {{ vmId }} 
                  <span @click="removeVm(index)" style="margin-left: 5px; font-weight: bold;">X</span>
                </label>
              </div>
            </div>


            <!-- VM :: Deployment Type -->
            <div class="mb-3">
              <label class="form-label">Deployment Type</label>
              <p class="text-muted">Select the deployment type</p>
              <div style="display: flex; gap: 10px;">
                <div class="form-check">
                  <input class="form-check-input" type="radio" id="Standalone" v-model="selectDeploymentType" value="Standalone">
                  <label class="form-check-label" for="Standalone">Standalone</label>
                </div>
                <div class="form-check">
                  <input class="form-check-input" type="radio" id="Clustering" v-model="selectDeploymentType" value="Clustering">
                  <label class="form-check-label" for="Clustering">Clustering</label>
                </div>
              </div>
            </div>

            <!-- VM :: Application -->
            <div class="mb-3">
              <label class="form-label">Application</label>
              <p class="text-muted">Select the application</p>
              <select 
                class="form-select" 
                v-model="inputApplications" 
                @change="onChangeCatalog">
                <option v-for="(catalog, idx) in catalogList" :key="idx" :value="catalog.name">
                  [{{ catalog.name }}] {{ catalog.packageInfo?.packageVersion ? catalog.packageInfo?.packageVersion : "latest" }}
                </option>
              </select>
            </div>

            <!-- VM :: Service Port -->
            <div class="mb-3">
              <label class="form-label">Port</label>
              <p class="text-muted">Please enter a port accessible from the outside</p>
              <input type="number"  class="form-control" placeholder="8080"  v-model="inputServicePort">
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
                  id="namesapce" 
                  v-model="selectNsId" 
                  @change="onSelectNamespace">
                  <option 
                    v-for="ns in nsIdList" 
                    :value=ns.name 
                    :key="ns.name">
                    {{ ns.name }}
                  </option>
                </select>
              </template>

              <template v-else>
                <select 
                  class="form-select" 
                  id="namesapce" 
                  v-model="selectNsId" 
                  @change="onChangeNsId">
                  <option 
                    value="selectNsId">
                    {{ selectNsId }}
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
                id="mci-name" 
                :disabled="selectNsId == ''" 
                v-model="selectCluster">
                <option 
                  v-for="cluster in clusterList" 
                  :value=cluster.id 
                  :key="cluster.name">
                  {{ cluster.name }}
                </option>
              </select>
            </div>

            <!-- K8S :: Helm Chart -->
            <div class="mb-3">
              <label class="form-label">Helm chart</label>
              <p class="text-muted">Select the application</p>
              <select class="form-select" v-model="inputApplications" @change="onChangeCatalog">
                <option v-for="(catalog, idx) in catalogList" :key="idx" :value="catalog.name">{{ catalog.name }} / {{ catalog.helmChart?.chartVersion ? catalog.helmChart?.chartVersion : "latest" }}</option>
              </select>
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

            <!-- K8S :: Ingress -->
            <div class="mb-3" v-if="modalTitle == 'Application Installation'">
              <label class="form-label">Ingress Configuration</label>
              
              <!-- Ingress Enabled -->
              <div class="mb-2">
                <div class="form-check">
                  <input 
                    class="form-check-input" 
                    type="checkbox" 
                    id="ingressEnabled" 
                    v-model="ingressData.ingressEnabled">
                  <label class="form-check-label" for="ingressEnabled">
                    Enable Ingress
                  </label>
                </div>
              </div>

              <!-- Ingress Fields (shown when enabled) -->
              <div v-if="ingressData.ingressEnabled">
                <!-- Ingress Host -->
                <div class="mb-2">
                  <label class="form-label">Host</label>
                  <input 
                    type="text" 
                    class="form-control" 
                    placeholder="example.com" 
                    v-model="ingressData.ingressHost">
                </div>

                <!-- Ingress Path -->
                <div class="mb-2">
                  <label class="form-label">Path</label>
                  <input 
                    type="text" 
                    class="form-control" 
                    placeholder="/" 
                    v-model="ingressData.ingressPath">
                </div>

                <!-- Ingress Class -->
                <div class="mb-2">
                  <label class="form-label">Ingress Class</label>
                  <input 
                    type="text" 
                    class="form-control" 
                    placeholder="nginx" 
                    v-model="ingressData.ingressClass">
                </div>

                <!-- TLS Configuration -->
                <div class="mb-2">
                  <div class="form-check">
                    <input 
                      class="form-check-input" 
                      type="checkbox" 
                      id="ingressTlsEnabled" 
                      v-model="ingressData.ingressTlsEnabled">
                    <label class="form-check-label" for="ingressTlsEnabled">
                      Enable TLS
                    </label>
                  </div>
                </div>

                <!-- TLS Secret (shown when TLS is enabled) -->
                <div v-if="ingressData.ingressTlsEnabled" class="mb-2">
                  <label class="form-label">TLS Secret Name</label>
                  <input 
                    type="text" 
                    class="form-control" 
                    placeholder="tls-secret" 
                    v-model="ingressData.ingressTlsSecret">
                </div>
              </div>
            </div>
          </template>
        </div>

        <!-- Footer -->
        <div 
          class="modal-footer d-flex justify-content-between">
          <a 
            class="btn btn-link link-secondary" 
            data-bs-dismiss="modal" 
            @click="setInit">
            Cancel
          </a>

          <div>
            <button 
              v-if="modalTitle == 'Application Installation'" 
              class="btn btn-danger ms-auto me-1" 
              @click="specCheck" 
              :disabled="!specCheckFlag">
              Spec Check
            </button>
            <button 
              class="btn btn-primary ms-auto" 
              data-bs-dismiss="modal" 
              @click="runInstall" 
              :disabled="specCheckFlag">
              Deploy
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
import { onMounted, watch, computed } from 'vue';
// @ts-ignore
import _, { slice } from 'lodash';
import { getNsInfo, getMciInfo, getVmInfo, getClusterInfo } from '@/api/tumblebug'
import { getSoftwareCatalogList, k8sSpecCheck, runK8SInstall, runAction, runVmInstall, vmSpecCheck } from '@/api/softwareCatalog'
import { type SoftwareCatalog } from '@/views/type/type'
import { useUserStore } from '@/stores/user'

interface Props {
  nsId: string
  title: string
}
const toast = useToast()
const userStore = useUserStore()

const props = defineProps<Props>()
const modalTitle = computed(() => props.title);

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
const selectDeploymentType = ref("Standalone" as string)
const hpaData = ref({} as any)
const ingressData = ref({} as any)

const clusterList = ref([] as any)
const selectCluster = ref("" as string)
const inputApplications = ref("" as string)
const inputServicePort = ref("" as string)
const specCheckFlag = ref(true as boolean)

// watch(modalTitle, async () => {
//   await setInit();
// });

// Handle deployment type changes
watch(selectDeploymentType, () => {
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

onMounted(async () => {
  const modalElement: any = document.getElementById('install-form');
  // Open Modal Action 
  modalElement.addEventListener('show.bs.modal', async() => {
    await setInit()
    await _getSoftwareCatalogList()
  });
})

const setInit = async () => {
  selectInfra.value = "VM"
  selectNsId.value = ""
  selectMci.value = ""
  selectVm.value = ""
  selectedVmList.value = []
  originalVmList.value = []
  selectDeploymentType.value = "Standalone"
  hpaData.value = {
    hpaEnabled: false,
    hpaMinReplicas: 1,
    hpaMaxReplicas: 10,
    hpaCpuUtilization: 60,
    hpaMemoryUtilization: 80
  }
  ingressData.value = {
    ingressEnabled: false,
    ingressHost: '',
    ingressPath: '/',
    ingressClass: 'nginx',
    ingressTlsEnabled: false,
    ingressTlsSecret: ''
  }

  setInfraList()
  setSpecCheckFlag()

  await _getNsId()
}

const _getSoftwareCatalogList = async () => {
  await getSoftwareCatalogList("").then(({ data }) => {
    catalogList.value = data
  })
}

const setInfraList = () => {
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

const _getNsId = async () => {
  await getNsInfo().then(async ({ data })=> {
    console.log('## data ### : ', data)
    nsIdList.value = data;

    if (nsIdList.value.length > 0) {
      // Priority 1: nsId passed from props
      if(!_.isEmpty(props.nsId)) {
        selectNsId.value = props.nsId
      }
      // Priority 2: projectInfo.ns_id from userStore (value saved in permission.ts)
      else if (!_.isEmpty(userStore.getNsId())) {
        const storeNsId = userStore.getNsId()
        // Check if the ns_id exists in nsIdList
        const foundNs = nsIdList.value.find((ns: any) => ns.name === storeNsId)
        if (foundNs) {
          selectNsId.value = storeNsId
        } else {
          // If not found, select the first item
          selectNsId.value = nsIdList.value[0].name
        }
      }
      // Priority 3: Default value (first item)
      else {
        selectNsId.value = nsIdList.value[0].name
      }
    }

    if (!_.isEmpty(selectNsId.value)) {
      if (selectInfra.value === 'VM')
        await _getMciName()
      else if(selectInfra.value === 'K8S')
        await _getClusterName()
    }
  })
  

}

const _getMciName = async () => {
  await getMciInfo(selectNsId.value).then(async ({ data }) => {
    mciList.value = data;
    if(mciList.value.length > 0) {
      selectMci.value = mciList.value[0].name;
      await _getVmName();
    } else {
      selectMci.value = "";
    }  
  })
}

const _getVmName = async () => {
  const params = {
    nsId: selectNsId.value,
    mciId: selectMci.value
  }
  await getVmInfo(params).then(({ data }) => {
    originalVmList.value = data.vm;
    // Set vmList excluding VMs that are already in selectedVmList
    vmList.value = originalVmList.value.filter((vm: any) => 
      !selectedVmList.value.includes(vm.id)
    );
    selectVm.value = "";
  })
}

const _getClusterName = async () => {
  await getClusterInfo(selectNsId.value).then(({ data }) => {
    clusterList.value = data;
    if(clusterList.value.length > 0) {
      selectCluster.value = clusterList.value[0].name;
    } else {
      selectCluster.value = "";
    }
  })
}

const onChangeNsId = async () => {
  selectedVmList.value = [];
  await _getMciName();
  onChangeForm();
}

const onChangeMci = async () => {
  selectedVmList.value = [];
  await _getVmName();
  onChangeForm();
}

const onSelectNamespace = async () =>{
  await _getClusterName();
  onChangeForm();
}

const onChangeForm = () => {
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

const removeVm = (index: number) => {
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

const runInstall = async () => {
  let appList = [] as Array<String>
  let res = {} as any

  if (selectInfra.value === 'VM') {
    // History: The initial design has changed, currently only sending 1 Application (previously it could receive multiple apps)
    appList = inputApplications.value.split(",").map(item => item.toLowerCase().trim());
    
    let params = {} as any
    if (modalTitle.value == 'Application Installation') {
      // Generate clusterName (only required in Clustering mode)
      const clusterName = selectDeploymentType.value === "Clustering" 
        ? `${inputApplications.value}-cluster` 
        : `${inputApplications.value}-standalone`;
      
      params = {
        namespace: selectNsId.value,
        mciId: selectMci.value,
        vmIds: selectedVmList.value,
        clusterName: clusterName,
        catalogId: selectedCatalogIdx.value,
        servicePort: inputServicePort.value,
        username: "admin",
        deploymentType: selectInfra.value,
        vmDeploymentMode: selectDeploymentType.value.toUpperCase(),
      }
      res = await runVmInstall(params)
    } else {
      res = await runAction(params)
    }

    if(res.data) {
      toast.success('SUCCESS')
    } else {
      toast.error('FAIL')
    }
  }

  else if (selectInfra.value === 'K8S') {
    // History: The initial design has changed, currently only sending 1 Application (previously it could receive multiple apps)
    appList = inputApplications.value.split(",").map(item => item.toLowerCase().trim());
    let params = {
      namespace: selectNsId.value,
      clusterName: selectCluster.value,
      catalogId: selectedCatalogIdx.value,
      servicePort: inputServicePort.value,
      username: "",
      deploymentType: selectInfra.value,
      hpaEnabled: hpaData.value.hpaEnabled,
      minReplicas: hpaData.value.hpaMinReplicas,
      maxReplicas: hpaData.value.hpaMaxReplicas,
      cpuThreshold: hpaData.value.hpaCpuUtilization,
      memoryThreshold: hpaData.value.hpaMemoryUtilization,
      ingressEnabled: ingressData.value.ingressEnabled,
      ingressHost: ingressData.value.ingressHost,
      ingressPath: ingressData.value.ingressPath,
      ingressClass: ingressData.value.ingressClass,
      ingressTlsEnabled: ingressData.value.ingressTlsEnabled,
      ingressTlsSecret: ingressData.value.ingressTlsSecret
    }

    if(modalTitle.value == 'Application Installation') {
      res = await runK8SInstall(params)
    } else {
      res = await runAction(params)
    }

    if(res.data) {
      toast.success('SUCCESS')
    } else {
      toast.error('FAIL')
    }
  }
}

const specCheck = async () => {

  if (selectInfra.value === 'VM' || selectInfra.value === 'K8S') {
    specCheckCallback().then((checkedValue: boolean | null | undefined) => {
      let data = true;

      if (checkedValue === null) {
        toast.error('Please select all items')
        return;
      }

      else if (checkedValue === false) {
        let infraName = "";

        if (selectInfra.value === 'VM') infraName = "VM"
        else if (selectInfra.value === 'K8S') infraName = "CLUSTER"

        const comment = 'Your selected ' + infraName + ' has lower specifications than recommended. Would you like to continue with the installation?'
        data = confirm(comment)
      }

      if (data) {
        toast.success('Please click RUN')
        specCheckFlag.value = false
      }
    })
  }
  else {
    toast.error("Please Select Infra")
  }
}

const specCheckCallback = async () => {
  let result = false as boolean;

  if (selectInfra.value === 'VM') {
    if (
      selectNsId.value === "" ||
      selectMci.value === "" ||
      selectedVmList.value.length === 0 ||
      selectedCatalogIdx.value === 0) {
      return null;
    }
    else {
      // Spec check with the first VM among selected VMs (or all VMs could be checked)
      const params = {
        namespace: selectNsId.value,
        mciName: selectMci.value,
        vmName: selectedVmList.value[0],
        catalogId: selectedCatalogIdx.value 
      }

      await vmSpecCheck(params).then(({ data }) => {
        result = data
      })
    }
  }
  else if (selectInfra.value === 'K8S') {
    if (
      selectNsId.value === "" ||
      selectCluster.value === "" ||
      selectedCatalogIdx.value === 0) {
      toast.error('Please select all items')
      return;
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

const selectedCatalogIdx = ref(0 as number)
const onChangeCatalog = () => {
  if(modalTitle.value === 'Application Installation') specCheckFlag.value = true

  catalogList.value.forEach((catalogInfo) => {
    if (inputApplications.value === catalogInfo.name) {
      selectedCatalogIdx.value = catalogInfo.id
      return;
    }
  })
}

</script>
<style scoped>
.w-80-per {
  width: 80% !important;
}
.w-90-per {
  width: 90% !important;
}
</style>