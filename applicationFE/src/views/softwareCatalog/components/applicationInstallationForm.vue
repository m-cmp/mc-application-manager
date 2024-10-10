<template>
  <div class="modal" id="install-form" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">{{ popupTitle }}</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">
                <template v-if="modalType == 'vm_application_install'">                
                    <div class="mb-3">
                        <label class="form-label">Namespace</label>
                        <p>Select the namespace where the application will be installed</p>
                        <select class="form-select" id="namesapce" v-model="selectNsId" @change="onChangeNsId">
                            <option v-for="ns in nsIdList" :value=ns.name :key="ns.name">{{ ns.name }}</option>
                        </select>
                    </div>
                    <div class="mb-3">
                        <label class="form-label">MCI Name</label>
                        <p>Select the multi-cloud infrastructure information where the application will be deployed</p>
                        <select class="form-select" id="mci-name" :disabled="selectNsId == ''" v-model="selectMci">
                            <option v-for="mci in mciList" :value=mci.name :key="mci.name">{{ mci.name }}</option>
                        </select>
                    </div>
                    <div class="mb-3">
                        <label class="form-label">Application</label>
                        <p>applications to install (e.g. nginx,tomcat,mariadb,redis,grafana,prometheus)</p>
                        <input type="text" class="form-control" id="sc-title" name="title" placeholder="nginx, tomcat ..." v-model="inputApplications" />
                    </div>
                </template>
                <template v-else>
                    <div class="mb-3">
                        <label class="form-label">Title2</label>
                        <input type="text" class="form-control" id="sc-title" name="title" placeholder="Application name" />
                    </div>
                </template>
            </div>
            <div class="modal-footer">
                <a class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setInit">
                    Cancel
                </a>
                <a class="btn btn-primary ms-auto" data-bs-dismiss="modal" @click="runInstall">
                    RUN
                </a>
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
import _ from 'lodash';


const splitUrl = window.location.host.split(':');
const baseUrl = window.location.protocol + '//' + splitUrl[0] + ':18084'
// const baseUrl = "http://210.217.178.130:18084";

interface Props {
    nsId: string
    title: string
}
const toast = useToast()

const props = defineProps<Props>()
const modalTitle = computed(() => props.title);
const popupTitle = ref("" as string)
const modalType = ref("" as string)
const selectNsId = ref("" as string)
const nsIdList = ref([] as any)
const mciList = ref([] as any)
const selectMci = ref("" as string)
const inputApplications = ref("" as string)
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
    if(_.isEmpty(props.nsId)) {
        await _getNsId()
    } else {
        selectNsId.value = props.nsId
    }
  
}

const _getNsId = async () => {
    const response = await axios.get(baseUrl + '/cbtumblebug/ns');
    nsIdList.value = response.data;
    if(nsIdList.value.length > 0) {
        selectNsId.value = nsIdList.value[0].name
    }

    if(!_.isEmpty(selectNsId.value)) {
        await _getMciName();
    }
}

const _getMciName = async () => {
    const response = await axios.get(baseUrl + '/cbtumblebug/ns/' + selectNsId.value + '/mci');
    mciList.value = response.data;
    if(mciList.value.length > 0) {
        selectMci.value = mciList.value[0].name
    } else {
        selectMci.value = "";
    }
}

const onChangeNsId = async () => {
    await _getMciName();
}

const runInstall = async () => {
    if(modalType.value == 'vm_application_install') {
        const appList = inputApplications.value.split(",").map(item => item.trim());
        const param = {
            "namespace": selectNsId.value,
            "mciName": selectMci.value,
            "applications": appList
        }

        const res = await axios.post(baseUrl + '/ape/vm/install', param)
        if(res.data.message) {
            toast.success(res.data.message)
        }
    }

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