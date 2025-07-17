<template>
  <div class="tab-pane" id="tabs-service">
    <div class="card">
      <div class="card-header">
        <h3 class="card-title">Metadata Section</h3>
      </div>
      <div class="card-body">
        <div class="mb-3">
          <label class="form-label required">- Name</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.name" placeholder="name-01" />
        </div>
        <div class="mb-3">
          <label class="form-label required">- Namespace</label>
          <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.namespace" placeholder="namespace" />
        </div>
        <div class="mb-3">
          <label class="form-label">- Labels</label>
          <div class="generate-form" v-for="(item, idx) in serviceLabels" :key="idx">
            <input type="text" class="form-control w-33" name="example-password-input" v-model="item.key" placeholder="key" />
            <input type="text" class="form-control w-33" name="example-password-input" v-model="item.value" placeholder="value" />
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
        <h3 class="card-title">Spec Section</h3>
      </div>
      <div class="card-body">
        <div class="mb-3">
          <label class="form-label">- Selector</label>
          <div class="generate-form" v-for="(item, idx) in selector" :key="idx">
            <input type="text" class="form-control w-33" name="example-password-input" v-model="item.key" placeholder="key" />
            <input type="text" class="form-control w-33" name="example-password-input" v-model="item.value" placeholder="value" />
            <div class="btn-list">
              <button class="btn btn-primary" @click="addSelector" style="text-align: center !important;">
              <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                <path d="M12 5l0 14" />
                <path d="M5 12l14 0" />
              </svg>
            </button>
            <button class="btn btn-primary" @click="removeSelector(idx)">
              <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
                <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                <path d="M5 12l14 0" />
              </svg>
            </button>
            </div>
          </div>
        </div>
        <div class="mb-3">
          <div class="mt-4" v-for="(item, idx) in ports" :key="idx">
            <div class="btn-list">
              <label class="form-label">Ports</label>
              <button class="btn btn-primary" @click="addPort" style="text-align: center !important;">
                <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                  <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                  <path d="M12 5l0 14" />
                  <path d="M5 12l14 0" />
                </svg>
              </button>
              <button class="btn btn-primary" @click="removePort(idx)">
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
                  <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                  <path d="M5 12l14 0" />
                </svg>
              </button>
            </div>
            <div class="row" style="width:68% !important">
              <div class="col mt-4">
                <label class="form-label required">- Port</label>
                <input type="text" class="form-control" v-model="item.port" />
              </div>
              <div class="col mt-4">
                <label class="form-label required">- Target Port</label>
                <input type="text" class="form-control" v-model="item.targetPort" />
              </div>
            </div>
            <div class="row" style="width:68% !important">
              <div class="col mt-4">
                <label class="form-label">- Protocol</label>
                <input type="text" class="form-control" v-model="item.protocol" />
              </div>
              <div class="col mt-4">
                <label class="form-label">- Node Port</label>
                <input type="text" class="form-control" v-model="item.nodePort" />
              </div>
            </div>
            <div class="border-bottom" style="width: 100%; margin-top: 10px" v-if="ports.length > 1"></div>
          </div>
          
            <div class="row" style="width:68% !important">
              <div class="col mt-4">
                <label class="form-label">- Type</label>
                <input type="text" class="form-control" v-model="spec.type" />
              </div>
            </div>
          
        </div>
      </div>
    </div>

    <div class="btn-list justify-content-end mt-4">
      <a class="btn btn-primary" :class="{ 'disabled': !isFormValid }" @click="isFormValid ? onClickService() : null" data-bs-toggle='modal' data-bs-target='#modal-service'>GENERATE</a>
    </div>
    <YamlModal :yaml-data="yamlData" :title="title" />
  </div>
  
  

</template>

<script setup lang="ts">
// @ts-ignore
import type { Service } from '@/views/type/type';
import { ref, computed } from 'vue';
import { onMounted } from 'vue';
import { useToast } from 'vue-toastification';
// @ts-ignore
import { generateYamlService } from '@/api/yaml';
import YamlModal from './servcieModal.vue';

const toast = useToast()

/**
 * @Title formData 
 * @Desc pod 데이터
 */
 const title = ref("" as string)
 const serviceFormData = ref({} as Service)
 const metadata = ref({} as any)
 const serviceLabels = ref([] as any)
 const spec = ref({} as any)
 const selector = ref([] as any)
 const ports = ref([] as any)
 const type = ref("" as string)
 const yamlData = ref("" as string)

/**
 * @Title Form Validation
 * @Desc 필수값이 모두 입력되었는지 확인하는 computed 속성
 */
const isFormValid = computed(() => {
  // Metadata validation
  if (!metadata.value.name || metadata.value.name.trim() === '') {
    return false;
  }
  
  if (!metadata.value.namespace || metadata.value.namespace.trim() === '') {
    return false;
  }
  
  // Port validation
  for (const port of ports.value) {
    if (!port.port || port.port.trim() === '') {
      return false;
    }
    
    if (!port.targetPort || port.targetPort.trim() === '') {
      return false;
    }
  }
  
  return true;
});

 onMounted(async () => {
  await setInit();
})

const setInit = () => {
  title.value = "Service"
  metadata.value = {
    name: "",
    namespace: "",
    labels: {}
  }
  spec.value = {
    selector: {},
    ports: [],
    type: ""
  }
  serviceLabels.value.push({key: "", value:""})
  selector.value.push({key: "", value:""})
  ports.value.push({protocol: "", port: "", targetPort: "", nodePort: ""})
}

const onClickService = async () => {
  // ================= Validation ==================
  // Metadata validation
  if (!metadata.value.name || metadata.value.name.trim() === '') {
    toast.error('Please enter service name.');
    // Focus on name input
    const nameInput = document.querySelector('input[v-model="metadata.name"]') as HTMLInputElement;
    nameInput?.focus();
    return;
  }
  
  if (!metadata.value.namespace || metadata.value.namespace.trim() === '') {
    toast.error('Please enter namespace.');
    // Focus on namespace input
    const namespaceInput = document.querySelector('input[v-model="metadata.namespace"]') as HTMLInputElement;
    namespaceInput?.focus();
    return;
  }
  
  // Port validation
  for (let i = 0; i < ports.value.length; i++) {
    const port = ports.value[i];
    
    if (!port.port || port.port.trim() === '') {
      toast.error(`Please enter port for port ${i + 1}.`);
      // Focus on port input
      const portInputs = document.querySelectorAll('input[v-model="item.port"]') as NodeListOf<HTMLInputElement>;
      if (portInputs[i]) {
        portInputs[i].focus();
      }
      return;
    }
    
    if (!port.targetPort || port.targetPort.trim() === '') {
      toast.error(`Please enter target port for port ${i + 1}.`);
      // Focus on target port input
      const targetPortInputs = document.querySelectorAll('input[v-model="item.targetPort"]') as NodeListOf<HTMLInputElement>;
      if (targetPortInputs[i]) {
        targetPortInputs[i].focus();
      }
      return;
    }
  }

  const transformedObject = serviceLabels.value.reduce((acc: { [x: string]: any; }, item: { key: string | number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  metadata.value.labels = transformedObject;

  serviceFormData.value.metadata = metadata.value
  const transformedObjectSelector = selector.value.reduce((acc: { [x: string]: any; }, item: { key: string | number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  spec.value.selector = transformedObjectSelector;
  spec.value.ports = ports.value;
  serviceFormData.value.spec = spec.value

  const { data } = await generateYamlService(serviceFormData.value);
  yamlData.value = data;
}

const addLabel = () => {
  serviceLabels.value.push({
    key: "", value:""
  })
}

const removeLabel = (idx: number) => {
  if(serviceLabels.value.length !== 1) {
    serviceLabels.value.splice(idx, 1)
  }
}

const addSelector = () => {
  selector.value.push({
    key: "", value:""
  })
}

const removeSelector = (idx: number) => {
  if(selector.value.length !== 1) {
    selector.value.splice(idx, 1)
  }
}

const addPort = () => {
  ports.value.push({protocol: "", port: "", targetPort: "", nodePort: ""})
}

const removePort = (idx: number) => {
  if(ports.value.length !== 1) {
    ports.value.splice(idx, 1)
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