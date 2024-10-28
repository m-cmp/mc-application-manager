<template>
  <div class="modal" id="install-form" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            {{ modalType == 'vm_application_install' ? 'Application installation for VM' : modalType ==
            'vm_application_uninstall' ? 'Application uninstallation for VM' :
            modalType == 'helm_application_install' ? 'Application installation for k8s' : modalType ==
            'helm_application_uninstall' ? 'Application uninstallation for k8s' : '' }}
            {{ applicationName }}
          </h5>
          <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close" @click="setInit"></button>
        </div>
        <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">
          <template v-if="modalType == 'vm_application_install' || modalType == 'vm_application_uninstall'">
            <div class="mb-3">
              <label class="form-label">Namespace</label>
              <p v-if="modalType == 'vm_application_install'" class="text-muted">Select the namespace where the
                application will be installed</p>
              <p v-else-if="modalType == 'vm_application_uninstall'" class="text-muted">Select the namespace where the
                application will be uninstalled</p>
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
              <p v-if="modalType == 'vm_application_install'" class="text-muted">Select the multi-cloud infrastructure
                information where the application will be deployed</p>
              <p v-else-if="modalType == 'vm_application_uninstall'" class="text-muted">Remove the application and
                associated resources from the multi-cloud infrastructure</p>
              <select class="form-select" id="mci-name" :disabled="selectNsId == ''" v-model="selectMci"
                @change="onChangeMci">
                <option v-for="mci in mciList" :value=mci.id :key="mci.name">{{ mci.name }}</option>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label">VM Name</label>
              <p class="text-muted">Select the virtual machine (VM) within the chosen multi-cloud infrastructure where
                the application will be deployed</p>
              <select class="form-select" id="mci-name" :disabled="selectMci == ''" v-model="selectVm">
                <option v-for="vm in vmList" :value="vm.id" :key="vm.name">{{ vm.name }}</option>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label">Application</label>
              <p class="text-muted">applications to install (e.g. nginx,tomcat,mariadb,redis,grafana,prometheus)</p>
              <input type="text" class="form-control" id="sc-title" name="title" placeholder="nginx, tomcat ..."
                v-model="inputApplications" disabled />
            </div>
          </template>
          <template v-else-if="modalType == 'helm_application_install' || modalType == 'helm_application_uninstall'">
            <div class="mb-3">
              <label class="form-label">Namespace</label>
              <p v-if="modalType == 'helm_application_install'" class="text-muted">Select the namespace where the
                application will be installed</p>
              <p v-else-if="modalType == 'helm_application_uninstall'" class="text-muted">Select the namespace where the
                application will be uninstalled</p>
              <template v-if="nsIdList.length > 0">
                <select class="form-select" id="namesapce" v-model="selectNsId" @change="onSelectNamespace">
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
              <label class="form-label">ClusterName</label>
              <p v-if="modalType == 'helm_application_install'" class="text-muted">Select the name of the cluster where
                the application will be deployed</p>
              <p v-else-if="modalType == 'helm_application_uninstall'" class="text-muted">Remove the application and
                associated resources from the multi-cloud infrastructure</p>
              <select class="form-select" id="mci-name" :disabled="selectNsId == ''" v-model="selectCluster">
                <option v-for="cluster in clusterList" :value=cluster.id :key="cluster.name">{{ cluster.name }}</option>
              </select>
            </div>
            <div class="mb-3">
              <label class="form-label">Helm chart</label>
              <p class="text-muted">Helm Charts to Install (e.g. nginx,tomcat,mariadb,redis,grafana,prometheus)</p>
              <input type="text" class="form-control" id="sc-title" name="title" placeholder="nginx, tomcat ..."
                v-model="inputApplications" disabled />
            </div>
          </template>
        </div>
        <div class="modal-footer" style="display: flex; justify-content: space-between;">
          <a class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setInit">
            Cancel
          </a>

          <div>
            <button v-if="modalType !== 'vm_application_uninstall' && modalType !== 'helm_application_uninstall'" class="btn btn-danger ms-auto" @click="specCheck" style="margin-right: 5px;" :disabled="!specCheckFlag">
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
  applicationName: string
  catalogIdx: number
}
const toast = useToast()

const props = defineProps<Props>()
const modalTitle = computed(() => props.title);
const applicationName = computed(() => props.applicationName)
const popupTitle = ref("" as string)
const modalType = ref("" as string)
const selectNsId = ref("" as string)
const nsIdList = ref([] as any)
const mciList = ref([] as any)
const vmList = ref([] as any)
const selectMci = ref("" as string)
const selectVm = ref("" as string)
const clusterList = ref([] as any)
const selectCluster = ref("" as string)
const inputApplications = ref("" as string)
const specCheckFlag = ref(true as boolean)

watch(applicationName, async() => {
  await setInit();
})
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
  inputApplications.value = props.applicationName
  if(_.isEmpty(props.nsId)) {
      await _getNsId()
  } else {
      selectNsId.value = props.nsId
  }
  if (modalType.value === 'vm_application_uninstall' || modalType.value === 'helm_application_uninstall') specCheckFlag.value = false
  else specCheckFlag.value = true
}

const _getNsId = async () => {
    const response = await axios.get(baseUrl + '/cbtumblebug/ns');
    nsIdList.value = response.data;
    if(nsIdList.value.length > 0) {
        selectNsId.value = nsIdList.value[0].name;
        if(modalType.value == 'vm_application_install' || modalType.value == 'vm_application_uninstall') {
            await _getMciName()
        } else {
            await _getClusterName()
        }
    }

    if(!_.isEmpty(selectNsId.value)) {
        if(modalType.value == 'vm_application_install' || modalType.value == 'vm_application_uninstall') {
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
}

const onChangeMci = async () => {
    await _getVmName();
}

const onSelectNamespace = async () =>{
    await _getClusterName();
}

const runInstall = async () => {
    if(modalType.value == 'vm_application_install' || modalType.value == 'vm_application_uninstall') {
        let runUrl = "";
        if(modalType.value == 'vm_application_install') {
            runUrl = "/ape/vm/install"
        } else {
            runUrl = "/ape/vm/uninstall"
        }

        const appList = inputApplications.value.split(",").map(item => item.trim());
        const param = {
            "namespace": selectNsId.value,
            "mciName": selectMci.value,
            "vmName": selectVm.value,
            "application": appList
      }
        const res = await axios.post(baseUrl + runUrl, param)
        if(res.data.code == 200 && res.data.message) {
            toast.success(res.data.message)
        } else {
            toast.error(res.data.message)
        }
    } else if(modalType.value == "helm_application_install" || modalType.value == "helm_application_uninstall") {
        let runUrl = "";
        if(modalType.value == 'helm_application_install') {
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
  specCheckCallback().then((checkedValue:boolean) => {
    let data = true;
    let infraName = "";
    if (!checkedValue) {
      if (modalType.value === 'vm_application_install' || modalType.value === 'vm_application_uninstall') {
        infraName = "VM"
      }
      else if (modalType.value === 'helm_application_install' || modalType.value === 'helm_application_uninstall') {
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

const specCheckCallback = async () => {
  let result = false;
  let runUrl = "";
  let param = "";

  if (modalType.value === 'vm_application_install' || modalType.value === 'vm_application_uninstall') {
    runUrl = "/applications/vm/check/application"
    param = "?namespace=" + selectNsId.value + "&mciName=" + selectMci.value + "&vmName=" + selectVm.value + "&catalogId=" + props.catalogIdx
  }
  else if (modalType.value === 'helm_application_install' || modalType.value === 'helm_application_uninstall') {
    runUrl = "/applications/k8s/check/application"
    param = "?namespace=" + selectNsId.value + "&clusterName=" + selectCluster.value + "&catalogId=" + props.catalogIdx
  }
  result = await axios.get(baseUrl + runUrl + param)
  return result;
}

</script>
<style scoped>
.input-form {
    width: 100% !important;
    display: flex;
    gap: 10px;
    margin-bottom: 10px;
  }
</style>