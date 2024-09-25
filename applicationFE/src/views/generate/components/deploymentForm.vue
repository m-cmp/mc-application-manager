<template>
  <div class="tab-pane" id="tabs-deployment">
    <div class="card">
      <div class="card-header">
        <h3 class="card-title">Metadata 영역</h3>
      </div>
      <div class="card-body">
        <div class="mb-3">
          <label class="form-label required">- Name</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.name" placeholder="deployment-01" />
        </div>
        <div class="mb-3">
          <label class="form-label required">- Namespace</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.namespace" placeholder="namespace" />
        </div>
        <div class="mb-3">
          <label class="form-label">- Labels</label>
          <div class="generate-form" v-for="(item, idx) in deployLabels" :key="idx">
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
        <div class="mb-3">
          <label class="form-label">- Replicas</label>
          <input type="text" class="form-control w-33" v-model="spec.replicas" />
        </div>
        <div class="mb-3">
          <label class="form-label">- Match Labels</label>
          <div class="generate-form" v-for="(item, idx) in matchLabels" :key="idx">
            <input type="text" class="form-control w-33" v-model="item.key" placeholder="key" />
            <input type="text" class="form-control w-33" v-model="item.value" placeholder="value" />
            <div class="btn-list">
              <button class="btn btn-primary" @click="addMatchLabel" style="text-align: center !important;">
              <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                <path d="M12 5l0 14" />
                <path d="M5 12l14 0" />
              </svg>
            </button>
            <button class="btn btn-primary" @click="removeMatchLabel(idx)">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
                <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                <path d="M5 12l14 0" />
              </svg>
            </button>
            </div>
          </div>
        </div>
        <div class="mb-3">
          <label class="form-label">Template</label>
        </div>
        <div class="mb-3">
          <div class="btn-list">
            <label class="form-label">- Labels</label>
          </div>
          <div class="generate-form" v-for="(item, idx) in templateLabels" :key="idx">
            <input type="text" class="form-control w-33" v-model="item.key" placeholder="key" />
            <input type="text" class="form-control w-33" v-model="item.value" placeholder="value" />
            <div class="btn-list">
              <button class="btn btn-primary" @click="addTemplateLabel" style="text-align: center !important;">
              <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                <path d="M12 5l0 14" />
                <path d="M5 12l14 0" />
              </svg>
            </button>
            <button class="btn btn-primary" @click="removeTemplateLabel(idx)">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
                <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                <path d="M5 12l14 0" />
              </svg>
            </button>
            </div>
          </div>
        </div>

      <div v-for="(container, idx) in containers" :key="idx">
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
          <label class="form-label">- Port</label>
          <div class="generate-form" v-for="(port, portIndex) in container.ports" :key="portIndex">
            <input type="text" class="form-control w-33" v-model="port.containerPort" placeholder="value" />
            <div class="btn-list">
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
      </div>
      </div>
    </div>

    <div class="btn-list justify-content-end mt-4">
      <a class="btn btn-primary" @click="onClickPod" data-bs-toggle='modal' data-bs-target='#modal-deploy'>GENERATE</a>
    </div>
    <YamlModal :yaml-data="yamlData" :title="title" />
  </div>
  
  

</template>

<script setup lang="ts">
import type { Deployment } from '@/views/type/type';
import { ref } from 'vue';
import { onMounted } from 'vue';
import { useToast } from 'vue-toastification';
import { generateYamlDeployment } from '@/api/yaml.ts';
import YamlModal from './deployModal.vue';

/**
 * @Title formData 
 * @Desc pod 데이터
 */
 const title = ref("" as string)
 const deployFormData = ref({} as Deployment)
 const metadata = ref({} as any)
 const deployLabels = ref([] as any)
 const matchLabels = ref([] as any)
 const templateLabels = ref([] as any)
 const spec = ref({} as any)
 const containers = ref([] as any)
 const yamlData = ref("" as string)

 onMounted(async () => {
  await setInit();
})

const setInit = () => {
  title.value = "Deployment"
  metadata.value.name = ""
  metadata.value.namespace = ""
  deployLabels.value.push({key: "", value: ""})
  matchLabels.value.push({key: "", value: ""})
  templateLabels.value.push({key: "", value: ""})

  spec.value = {
    replicas: "",
    selector: {
      matchLabels: {}
    },
    template: {
      metadata: {
        labels:{}
      },
      spec: {
        containers: []
      }
    }
  }

  containers.value.push({
    name: "",
    image: "",
    env: [{name: "", value:""}],
    ports: [{containerPort: ""}],
  })
}

const onClickPod = async () => {
  const transformedObject = deployLabels.value.reduce((acc: { [x: string]: any; }, item: { key: string|number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  const transformedObjectMatch = matchLabels.value.reduce((acc: { [x: string]: any; }, item: { key: string|number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  const transformedObjectTemplate = templateLabels.value.reduce((acc: { [x: string]: any; }, item: { key: string|number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  metadata.value.labels = transformedObject;
  deployFormData.value.metadata = metadata.value;
  spec.value.selector.matchLabels = transformedObjectMatch;
  spec.value.template.metadata.labels = transformedObjectTemplate;
  spec.value.template.spec.containers =  containers.value;
  deployFormData.value.spec = spec.value;

  console.log("deployFormData.value : ", deployFormData.value)
  
  const { data } = await generateYamlDeployment(deployFormData.value);
  yamlData.value = data;
}

const addLabel = () => {
  deployLabels.value.push({
    key: "", value:""
  })
}

const removeLabel = (idx: number) => {
  if(deployLabels.value.length !== 1) {
    deployLabels.value.splice(idx, 1)
  }
}

const addMatchLabel = () => {
  matchLabels.value.push({
    key: "", value:""
  })
}

const removeMatchLabel = (idx: number) => {
  if(matchLabels.value.length !== 1) {
    matchLabels.value.splice(idx, 1)
  }
}

const addTemplateLabel = () => {
  templateLabels.value.push({
    key: "", value:""
  })
}

const removeTemplateLabel = (idx: number) => {
  if(templateLabels.value.length !== 1) {
    templateLabels.value.splice(idx, 1)
  }
}

const addContainer = () => {
  containers.value.push({
    name: "",
    image: "",
    env: [{name: "", value:""}],
    ports: [{containerPort: ""}],
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
    containerPort: ""
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