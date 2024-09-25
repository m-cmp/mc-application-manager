<template>
  <div class="tab-pane active show" id="tabs-pod">
    <div class="card">
      <div class="card-header">
        <h3 class="card-title">Metadata 영역</h3>
      </div>
      <div class="card-body">
        <div class="mb-3">
          <label class="form-label required">- Name</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.name" placeholder="pod-01" />
        </div>
        <div class="mb-3">
          <label class="form-label required">- Namespace</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.namespace" placeholder="namespace" />
        </div>
        <div class="mb-3">
          <label class="form-label">- Labels</label>
          <div class="generate-form" v-for="(item, idx) in podLabels" :key="idx">
            <input type="text" class="form-control w-33" v-model="item.key" placeholder="key" />
            <input type="text" class="form-control w-33" v-model="item.value" placeholder="value" />
            <div class="btn-list">
              <button class="btn btn-primary" @click="addLabel" style="text-align: center !important;">
              <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                <path d="M12 5l0 14" />
                <path d="M5 12l14 0" />
              </svg>
            </button>
            <button class="btn btn-primary" @click="removeLabel(idx)">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
                <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                <path d="M5 12l14 0" />
              </svg>
            </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="card mt-4">
      <div class="card-header">
        <h3 class="card-title">Spec 영역</h3>
      </div>
      <div class="card-body">
        <div class="mt-4" v-for="(container, idx) in containers" :key="idx">
          <div class="mb-3">
            <div class="btn-list">
              <label class="form-label">Containers</label>
              <button class="btn btn-primary" @click="addContainer" style="text-align: center !important;">
                <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                  <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                  <path d="M12 5l0 14" />
                  <path d="M5 12l14 0" />
                </svg>
              </button>
              <button class="btn btn-primary" @click="removeContainer(idx)">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
                  <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                  <path d="M5 12l14 0" />
                </svg>
              </button>
            </div>
            <div class="row" style="width:68% !important">
              <div class="col mt-4">
                <label class="form-label required">- Name</label>
                <input type="text" class="form-control" v-model="container.name" />
              </div>
              <div class="col mt-4">
                <label class="form-label required">- Image</label>
                <input type="text" class="form-control" v-model="container.image" />
              </div>
            </div>
          </div>
          <div class="mb-3">
            <label class="form-label">- Env</label>
            <div class="generate-form" v-for="(env, envIndex) in container.env" :key="envIndex">
              <input type="text" class="form-control w-33" v-model="env.name" placeholder="key" />
              <input type="text" class="form-control w-33" v-model="env.value" placeholder="value" />
              <div class="btn-list">
                <button class="btn btn-primary" @click="addEnv(idx)" style="text-align: center !important;">
                  <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                    <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                    <path d="M12 5l0 14" />
                    <path d="M5 12l14 0" />
                  </svg>
                </button>
                <button class="btn btn-primary" @click="removeEnv(idx, envIndex)">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
                    <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                    <path d="M5 12l14 0" />
                  </svg>
                </button>
              </div>
            </div>
          </div>
          <div class="mb-3"> 
            <div class="mt-4" v-for="(port, portIndex) in container.ports" :key="portIndex">
              <div class="btn-list">
                <label class="form-label">Ports</label>
                <button class="btn btn-primary" @click="addPort(idx)" style="text-align: center !important;">
                  <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                    <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                    <path d="M12 5l0 14" />
                    <path d="M5 12l14 0" />
                  </svg>
                </button>
                <button class="btn btn-primary" @click="removePort(idx, portIndex)">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
                    <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                    <path d="M5 12l14 0" />
                  </svg>
                </button>
              </div>
              <div class="row" style="width:68% !important">
                <div class="col mt-4">
                  <label class="form-label">- Name</label>
                  <input type="text" class="form-control" v-model="port.name" />
                </div>
                <div class="col mt-4">
                  <label class="form-label">- Port</label>
                  <input type="text" class="form-control" v-model="port.containerPort" />
                </div>
              </div>
              <div class="row" style="width:68% !important">
                <div class="col mt-4">
                  <label class="form-label">- Protocol</label>
                  <input type="text" class="form-control" v-model="port.protocol" />
                </div>
                <div class="col mt-4">
                  <label class="form-label">- Host Port</label>
                  <input type="text" class="form-control" v-model="port.hostPort" />
                </div>
              </div>
              <div class="border-bottom" style="width: 100%; margin-top: 10px" v-if="container.ports.length > 1"></div>
            </div>
          </div>
          <div class="mb-3">
            <div class="btn-list">
              <label class="form-label">Resources</label>
            </div>
            <div class="row" style="width:68% !important">
              <div class="col mt-4">
                <label class="form-label">- Limits CPU</label>
                <input type="text" class="form-control" v-model="container.resources.limits.cpu" />
              </div>
              <div class="col mt-4">
                <label class="form-label">- Limits Memory</label>
                <input type="text" class="form-control" v-model="container.resources.limits.memory" />
              </div>
            </div>
            <div class="row" style="width:68% !important">
              <div class="col mt-4">
                <label class="form-label">- Requests CPU</label>
                <input type="text" class="form-control" v-model="container.resources.requests.cpu" />
              </div>
              <div class="col mt-4">
                <label class="form-label">- Requests Memory</label>
                <input type="text" class="form-control" v-model="container.resources.requests.memory" />
              </div>
            </div>
          </div>
          <div class="border-bottom" style="width: 100%; margin-top: 10px" v-if="containers.length > 1"></div>
        </div>
        <div class="mb-3">
          <div class="mt-4">
            <label class="form-label">- Restart Policy</label>
            <input type="text" class="form-control w-33" v-model="spec.restartPolicy" />
          </div>
        </div>

      </div>
    </div>

    <div class="btn-list justify-content-end mt-4">
      <a class="btn btn-primary" @click="onClickPod" data-bs-toggle='modal' data-bs-target='#modal-pod'>GENERATE</a>
    </div>
    <YamlModal :yaml-data="yamlData" :title="title" />
  </div>
  
  

</template>

<script setup lang="ts">
import type { Pod } from '@/views/type/type';
import { ref, reactive } from 'vue';
import { onMounted } from 'vue';
import { useToast } from 'vue-toastification';
import { generateYamlPod } from '@/api/yaml.ts';
import YamlModal from './podModal.vue';

/**
 * @Title formData 
 * @Desc pod 데이터
 */
 const title = ref("" as string)
 const podFormData = ref({} as Pod)
 const metadata = ref({} as any)
 const podLabels = ref([] as any)
 const spec = ref({} as any)
 const containers = ref([] as any)
 const yamlData = ref("" as string)

 onMounted(async () => {
  await setInit();
})

const setInit = () => {
  title.value = "Pod"
  metadata.value.name = ""
  metadata.value.namespace = ""
  podLabels.value.push({key: "", value: ""})

  spec.value = {
    containers : [],
    restartPolicy: ""
  }

  containers.value.push({
    name: "",
    image: "",
    env: [{name: "", value:""}],
    ports: [{name: "", containerPort: "", hostPort: "", protocol: ""}],
    resources: {
      limits: {
        memory: "",
        cpu: "",
      },
      requests: {
        memory: "",
        cpu: ""
      }
    },
  })
}

const onClickPod = async () => {
  const transformedObject = podLabels.value.reduce((acc: { [x: string]: any; }, item: { key: string|number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  metadata.value.labels = transformedObject;
  podFormData.value.metadata = metadata.value;
  spec.value.containers =  containers.value;
  podFormData.value.spec = spec.value;
  
  const { data } = await generateYamlPod(podFormData.value);
  yamlData.value = data;
}

const addLabel = () => {
  podLabels.value.push({
    key: "", value:""
  })
}

const removeLabel = (idx: number) => {
  if(podLabels.value.length !== 1) {
    podLabels.value.splice(idx, 1)
  }
}

const addContainer = () => {
  containers.value.push({
    name: "",
    image: "",
    env: [{name: "", value:""}],
    ports: [{name: "", containerPort: "", hostPort: "", protocol: ""}],
    resources: {
      limits: {
        memory: "",
        cpu: "",
      },
      requests: {
        memory: "",
        cpu: ""
      }
    },
  })
}

const removeContainer = (idx: number) => {
  if(containers.value.length !== 1) {
    containers.value.splice(idx, 1)
  }
}

const addEnv = (idx: number) => {
  containers.value[idx].env.push({
    name: "", value:""
  })
}

const removeEnv = (idx: number, envIndex: number) => {
  if(containers.value[idx].env.length !== 1) {
    containers.value[idx].env.splice(envIndex, 1)
  }
}

const addPort = (idx: number) => {
  containers.value[idx].ports.push({
    name: "", containerPort: "", hostPort: "", protocol: ""
  })
}

const removePort = (idx: number, portIndex: number) => {
  if(containers.value[idx].ports.length !== 1) {
    containers.value[idx].ports.splice(portIndex, 1)
  }
}

</script>
<style scoped>
.generate-form {
  width: 100% !important;
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}
</style>