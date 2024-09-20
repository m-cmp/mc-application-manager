<template>
  <div class="tab-pane active show" id="tabs-pod">
    <!-- <h4>Pod</h4> -->
    <div class="mb-3">
      <label class="form-label required">Pod Name</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.name" placeholder="pod-01" />
    </div>
    <div class="mb-3">
      <label class="form-label">Namespace</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="metadata.namespace" placeholder="namespace" />
    </div>
    <div class="mb-3">
      <label class="form-label">Labels</label>
      <div class="generate-form" v-for="(item, idx) in podLabels" :key="idx">
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
    <div class="mb-3">
      <label class="form-label">Image</label>
      <input type="text" class="form-control w-33" name="example-text-input" v-model="image" placeholder="image" />
    </div>
    <div class="mb-3">
      <label class="form-label">env</label>
      <div class="generate-form" v-for="(item, idx) in podEnv" :key="idx">
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.name" placeholder="name" />
        <input type="text" class="form-control w-33" name="example-password-input" v-model="item.value" placeholder="value" />
        <div class="btn-list">
          <button class="btn btn-primary" @click="addEnv" style="text-align: center !important;">
          <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
            <path d="M12 5l0 14" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        <button class="btn btn-primary" @click="removeEnv(idx)">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        </div>
      </div>
    </div>
    <div class="mb-3">
      <div class="btn-list">
        <label class="form-label">Container</label>
        <button class="btn btn-primary" @click="addPort" style="text-align: center !important;">
          <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
            <path d="M12 5l0 14" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
        <button class="btn btn-primary">
          <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-minus" style="margin: 0 !important;">
            <path stroke="none" d="M0 0h24v24H0z" fill="none" />
            <path d="M5 12l14 0" />
          </svg>
        </button>
      </div>

      <div class="row" style="width:68% !important">
        <div class="col mt-4">
          <label class="form-label">Name</label>
          <input type="text" class="form-control" name="example-password-input" />
        </div>
        <div class="col mt-4">
          <label class="form-label">Port</label>
          <input type="text" class="form-control" name="example-password-input" />
        </div>
      </div>

      <div class="row" style="width:68% !important">
        <div class="col">
          <label class="form-label">Protocol</label>
          <input type="text" class="form-control" name="example-password-input" />
        </div>
        <div class="col">
          <label class="form-label">Host Port</label>
          <input type="text" class="form-control" name="example-password-input" />
        </div>
      </div>

      <div class="row">
        <div class="col mt-4">
          <label class="form-label">Resources</label>
        </div>
      </div>

      <div class="row" style="width:68% !important">
        <div class="col">
          <label class="form-label">Limits CPU</label>
          <input type="text" class="form-control" name="example-password-input" />
        </div>
        <div class="col">
          <label class="form-label">Limits Memory</label>
          <input type="text" class="form-control" name="example-password-input" />
        </div>
      </div>
      <div class="row" style="width:68% !important">
        <div class="col">
          <label class="form-label">Requests CPU</label>
          <input type="text" class="form-control" name="example-password-input" />
        </div>
        <div class="col">
          <label class="form-label">Requests Memory</label>
          <input type="text" class="form-control" name="example-password-input" />
        </div>
      </div>
      <div class="row" style="width:68% !important">
        <div class="col">
          <label class="form-label">Restart Policy</label>
          <input type="text" class="form-control" name="example-password-input" v-model="restartPolicy" />
        </div>
      </div>
    </div>
    

    <!-- <div class="mb-3">
      <div class="accordion" id="accordion-example">
        <div class="accordion-item">
          <h2 class="accordion-header" id="heading-1">
            <button class="accordion-button collapsed" type="button" data-bs-toggle="collapse" data-bs-target="#collapse-1" aria-expanded="true">
              Labels
            </button>
          </h2>
          <div id="collapse-1" class="accordion-collapse collapse" data-bs-parent="#accordion-example" style="">
            <div class="accordion-body pt-0">
              <strong>This is the first item's accordion body.</strong> It is hidden by default, until the collapse plugin adds the appropriate classes that we use to style each element. These classes control the overall appearance, as well as the showing and hiding via CSS transitions. You can modify any of this with custom CSS or overriding our default variables. It's also worth noting that just about any HTML can go within the <code>.accordion-body</code>, though the transition does limit overflow.
            </div>
          </div>
        </div>
      </div>
    </div> -->

    <div class="btn-list justify-content-end">
      <a class="btn btn-primary" @click="onClickPod">GENERATE</a>
    </div>
  </div>
  
  

</template>

<script setup lang="ts">
import type { Pod } from '@/views/type/type';
import { ref, reactive } from 'vue';
import { onMounted } from 'vue';
import { useToast } from 'vue-toastification';

/**
 * @Title formData 
 * @Desc pod 데이터
 */
 const podFormData = ref({} as Pod)
 const metadata = ref({} as any)
 const podLabels = ref([] as any)
 const podContainers = ref([] as any)
 const podEnv = ref([] as any)
 const podVoumeMounts = ref([] as any)
 const restartPolicy = ref("" as string)
 const image = ref("" as string)
 const ports = ref([] as any)

 onMounted(async () => {
  await setInit();
})

const setInit = () => {
  metadata.value.name = ""
  metadata.value.namespace = ""
  restartPolicy.value = ""
  image.value = ""
  podLabels.value.push({key: "", value:""})
  podEnv.value.push({name: "", value:""})

}

const onClickPod = () => {
  const transformedObject = podLabels.value.reduce((acc: { [x: string]: any; }, item: { key: string|number; value: any; }) => {
    acc[item.key] = item.value;
    return acc;
  }, {});

  console.log("transformedObject : ", transformedObject)
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

const addPort = () => {
  podContainerPort.value.push({
    portNum: "", name:""
  })
}

const removePort = (idx: number) => {
  if(podContainerPort.value.length !== 1) {
    podContainerPort.value.splice(idx, 1)
  }
}

const addEnv = () => {
  podEnv.value.push({
    name: "", value:""
  })
}

const removeEnv = (idx: number) => {
  if(podEnv.value.length !== 1) {
    podEnv.value.splice(idx, 1)
  }
}

const addVm = () => {
  podVoumeMounts.value.push({
    mountPath: "", name:""
  })
}

const removeVm = (idx: number) => {
  if(podVoumeMounts.value.length !== 1) {
    podVoumeMounts.value.splice(idx, 1)
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