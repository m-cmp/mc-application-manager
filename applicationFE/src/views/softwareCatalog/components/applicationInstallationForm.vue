<template>
  <div class="modal" id="install-form" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            {{ modalType }}
          </h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" @click="setInit"></button>
        </div>
        <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">

          <div class="mb-3">
            <label class="form-label">Target Infra</label>
            <p 
              v-if="modalType == 'Application Installation'" 
              class="text-muted">
                Select the Infra what is the Infra will be installed
            </p>
            <p 
              v-else-if="modalType == 'Application Uninstallation'" 
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

          <template v-if="selectInfra == 'VM'">
            <div class="mb-3">
              <label class="form-label">Namespace</label>
              <p 
                v-if="modalType == 'Application Installation'" 
                class="text-muted">
                Select the namespace where the application will be installed</p>
              <p 
                v-else-if="modalType == 'Application Uninstallation'" 
                class="text-muted">
                Select the namespace where the application will be uninstalled</p>
              
              <template v-if="nsIdList.length > 0">
                <select class="form-select" id="namesapce" v-model="selectNsId" @change="onChangeNsId">
                  <option v-for="ns in nsIdList" :value=ns.name :key="ns.name">{{ ns.name }}</option>
                </select>
              </template>
              
              <template v-else>
                <select class="form-select" id="namesapce" v-model="selectNsId" @change="onChangeNsId">
                  <option value="selectNsId">{{ selectNsId }}</option>
                </select>
              </template>
            </div>

            <div class="mb-3">
              <label class="form-label">MCI Name</label>
              <p 
                v-if="modalType == 'Application Installation' && selectInfra == 'VM'" 
                class="text-muted">
                Select the multi-cloud infrastructure information where the application will be deployed</p>
              <p 
                v-else-if="modalType == 'Application Uninstallation' && selectInfra == 'VM'" 
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
                  :key="mci.name">{{ mci.name }}</option>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label">VM Name</label>
              <p 
                class="text-muted">
                Select the virtual machine (VM) within the chosen multi-cloud infrastructure where the application will be deployed</p>
              <select 
                class="form-select" 
                id="mci-name" 
                :disabled="selectMci == ''" 
                v-model="selectVm">
                <option 
                  v-for="vm in vmList" 
                  :value="vm.id" 
                  :key="vm.name">
                  {{ vm.name }}
                </option>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label">Application</label>
              <p class="text-muted">Select the application</p>
              <select class="form-select" v-model="inputApplications" @change="onChangeCatalog">
                <option v-for="catalog in props.catalogList" :key="catalog">{{ catalog.catalogTitle }}</option>
              </select>

              <!-- <input 
                type="text" 
                class="form-control" 
                id="sc-title" 
                name="title" 
                placeholder="nginx, tomcat ..."
                v-model="inputApplications" /> -->
            </div>
          </template>

          <template v-else-if="selectInfra == 'K8S'">
            <div class="mb-3">
              <label class="form-label">Namespace</label>
              <p 
                v-if="modalType == 'Application Installation'" 
                class="text-muted">Select the namespace where the application will be installed</p>
              <p 
                v-else-if="modalType == 'Application Uninstallation'" 
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

            <div class="mb-3">
              <label class="form-label">ClusterName</label>
              <p 
                v-if="modalType == 'Application Installation'" 
                class="text-muted">Select the name of the cluster where the application will be deployed</p>
              <p 
                v-else-if="modalType == 'Application Uninstallation'" 
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
            <div class="mb-3">
              <label class="form-label">Helm chart</label>
              <p class="text-muted">Select the application</p>
              <select class="form-select" v-model="inputApplications" @change="onChangeCatalog">
                <option v-for="catalog in props.catalogList" :key="catalog">{{ catalog.catalogTitle }}</option>
              </select>
              <!-- <input 
                type="text" 
                class="form-control" 
                id="sc-title" 
                name="title" 
                placeholder="nginx, tomcat ..."
                v-model="inputApplications" /> -->
            </div>

            <!-- <div class="mb-3" v-if="modalType == 'Application Installation' && selectInfra === 'K8S'" >
              <label class="form-label">HPA</label>
              <div style="display: flex; justify-content: space-between;">
                <div>
                  <label class="form-label required">minReplicas</label>
                  <input type="number" class="form-control w-90-per" placeholder="1" v-model="hpaData.hpaMinReplicas" />
                </div>
                <div>
                  <label class="form-label required">maxReplicas</label>
                  <input type="number" class="form-control w-90-per" placeholder="10" v-model="hpaData.hpaMaxReplicas" />
                </div>
                <div>
                  <div>
                    <label class="form-check-label d-inline">CPU (%)</label>
                  </div>
                  <input type="number" class="form-control w-80-per d-inline" placeholder="60" v-model="hpaData.hpaCpuUtilization" /> %
                </div>
                <div>
                  <div>
                    <label class="form-check-label d-inline" >MEMORY (%)</label>
                  </div>
                  <input type="number" class="form-control w-80-per d-inline" placeholder="80" v-model="hpaData.hpaMemoryUtilization" /> %
                </div>
              </div>
            </div> -->
            
          </template>
        </div>
        <div class="modal-footer" style="display: flex; justify-content: space-between;">
          <a class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setInit">
            Cancel
          </a>

          <div>
            <button v-if="modalType == 'Application Installation'" class="btn btn-danger ms-auto" @click="specCheck" style="margin-right: 5px;" :disabled="!specCheckFlag">
              Spec Check
            </button>
            <button class="btn btn-primary ms-auto" data-bs-dismiss="modal" @click="runInstall" :disabled="specCheckFlag">
              RUN
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
import axios from 'axios'
import _, { slice } from 'lodash';


const splitUrl = window.location.host.split(':');
const baseUrl = window.location.protocol + '//' + splitUrl[0] + ':18084'
// const baseUrl = "http://15.164.227.13:18084";
// const baseUrl = "http://192.168.6.30:18084";

interface Props {
  nsId: string
  title: string
  catalogList: Array<any>
}
const toast = useToast()

const props = defineProps<Props>()
const modalTitle = computed(() => props.title);
const popupTitle = ref("" as string)
const modalType = ref("" as string)

const infraList = ref([] as any)
const nsIdList = ref([] as any)
const mciList = ref([] as any)
const vmList = ref([] as any)

const selectInfra = ref("" as string)
const selectNsId = ref("" as string)
const selectMci = ref("" as string)
const selectVm = ref("" as string)
const hpaData = ref({} as any)

const clusterList = ref([] as any)
const selectCluster = ref("" as string)
const inputApplications = ref("" as string)
const specCheckFlag = ref(true as boolean)

// watch(inputApplications, async () => {
//   // TODO :: hpa 데이터 가져오는 API 필요
//   hpaData.value = {
//     hpaMinReplicas: 0,
//     hpaMaxReplicas: 0,
//     hpaCpuUtilization: 0,
//     hpaMemoryUtilization: 0
//   }
// })
watch(modalTitle, async () => {
  popupTitle.value = changeTitle(props.title);
  modalType.value = props.title;
  await setInit();
});
onMounted(async () => {
  await setInit()
})

const changeTitle = (text: string) => {
  return text.split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
}

const setInit = async () => {
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

  if(_.isEmpty(props.nsId)) {
      await _getNsId()
  } else {
      selectNsId.value = props.nsId
  }
  if (modalType.value === 'Application Uninstallation') specCheckFlag.value = false
  else specCheckFlag.value = true
}

const _getNsId = async () => {
  const response = await axios.get(baseUrl + '/cbtumblebug/ns');
  nsIdList.value = response.data;
  if(nsIdList.value.length > 0) {
    selectNsId.value = nsIdList.value[0].name;
    if(modalType.value == 'Application Installation' || modalType.value == 'Application Uninstallation') {
      await _getMciName()
    } 
    else {
      await _getClusterName()
    }
  }

  if (!_.isEmpty(selectNsId.value)) {
    if (selectInfra.value === 'VM') {
      await _getMciName()
    } else {
      await _getClusterName()
    }
  }
}

const _getMciName = async () => {
  const response = await axios.get(baseUrl + '/cbtumblebug/ns/' + selectNsId.value + '/mci');
  mciList.value = response.data;
  if(mciList.value.length > 0) {
    selectMci.value = mciList.value[0].name;
    await _getVmName();
  } else {
    selectMci.value = "";
  }
}

const _getVmName = async () => {
  const response = await axios.get(baseUrl + '/cbtumblebug/ns/' + selectNsId.value + '/mci/' + selectMci.value);
  vmList.value = response.data.vm;
  if(mciList.value.length > 0) {
    selectVm.value = vmList.value[0].name;
  } else {
    selectVm.value = "";
  }
}

const _getClusterName = async () => {
  const response = await axios.get(baseUrl + '/cbtumblebug/ns/' + selectNsId.value + '/k8scluster');
  clusterList.value = response.data;
  if(clusterList.value.length > 0) {
    selectCluster.value = clusterList.value[0].name;
  } else {
    selectCluster.value = "";
  }
}

const onChangeNsId = async () => {
  await _getMciName();
  if(modalType.value === 'Application Installation')
    onChangeForm();
}

const onChangeMci = async () => {
  await _getVmName();
  if(modalType.value === 'Application Installation')
    onChangeForm();
}

const onSelectNamespace = async () =>{
  await _getClusterName();
  if(modalType.value === 'Application Installation')
    onChangeForm();
}

const runInstall = async () => {
  if(selectInfra.value === 'VM') {
    let runUrl = "";
    if(modalType.value == 'Application Installation') {
      runUrl = "/ape/vm/install"
    } else {
      runUrl = "/ape/vm/uninstall"
    }

    const appList = inputApplications.value.split(",").map(item => item.trim());
    const param = {
      "namespace": selectNsId.value,
      "mciName": selectMci.value,
      "vmName": selectVm.value,
      "applications": appList
    }
    const res = await axios.post(baseUrl + runUrl, param)
    if(res.data.code == 200 && res.data.message) {
      toast.success(res.data.message)
    } else {
      toast.error(res.data.message)
    }
  } else if(selectInfra.value === 'K8S') {
    let runUrl = "";
    if(modalType.value == 'Application Installation') {
      runUrl = "/ape/helm/install"
    } else {
      runUrl = "/ape/helm/uninstall"
    }

    const appList = inputApplications.value.split(",").map(item => item.trim());
    const param = {
      "namespace": selectNsId.value,
      "clusterName": selectCluster.value,
      "helmCharts": appList
    }
    const res = await axios.post(baseUrl + runUrl, param)
    if(res.data.code == 200 && res.data.message) {
      toast.success(res.data.message)
    } else {
      toast.error(res.data.message)
    }

  }
}

const specCheck = async () => {
  if (selectInfra.value === 'VM' || selectInfra.value === 'K8S') {
    specCheckCallback().then((checkedValue: boolean) => {
      let data = true;
      let infraName = "";
      if (checkedValue === null) {
        toast.error('Please select all items')
        return;
      }
      if (!checkedValue) {
        if (selectInfra.value === 'VM') {
          infraName = "VM"
        }
        else if (selectInfra.value === 'K8S') {
          infraName = "CLUSTER"
        }

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
  let result = false;
  let runUrl = "";
  let param = "";

  if (selectInfra.value === 'VM') {
    console.log('inputApplications.value >> ', inputApplications.value)
    if (selectNsId.value === undefined || selectMci.value === undefined || selectVm.value === undefined || selectedCatalogIdx.value === undefined) {
      return null;
    }
    runUrl = "/applications/vm/check/application"
    param = "?namespace=" + selectNsId.value + "&mciName=" + selectMci.value + "&vmName=" + selectVm.value + "&catalogId=" + selectedCatalogIdx.value
  }
  else if (selectInfra.value === 'K8S') {
    if (selectNsId.value === undefined || selectCluster.value === undefined  || selectedCatalogIdx.value === undefined) {
      toast.error('Please select all items')
      return;
    }
    runUrl = "/applications/k8s/check/application"
    param = "?namespace=" + selectNsId.value + "&clusterName=" + selectCluster.value + "&catalogId=" + selectedCatalogIdx.value
  }
  result = await axios.get(baseUrl + runUrl + param)

  return result;
}

const selectedCatalogIdx = ref(0 as number)
const onChangeCatalog = () => {
  if(modalType.value === 'Application Installation')
    specCheckFlag.value = true

  props.catalogList.forEach((catalogInfo) => {
    if (inputApplications.value === catalogInfo.catalogTitle) {
      selectedCatalogIdx.value = catalogInfo.catalogIdx
      return
    }
  })
}
const onChangeForm = () => {
  if(modalType.value === 'Application Installation')
    specCheckFlag.value = true
}

</script>
<style scoped>
.input-form {
  width: 100% !important;
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}
.w-50-per {
  width: 50% !important;
}
.w-80-per {
  width: 80% !important;
}
.w-90-per {
  width: 90% !important;
}
.mr-5 {
  margin-right: 5px;
}
</style>