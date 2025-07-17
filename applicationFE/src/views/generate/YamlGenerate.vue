<template>
  <div class="card w-100" ref="workflowForm">
    <div class="card-header">
      <div class="card-title">
        <h1>YAML Generator</h1>
      </div>
    </div>
    <div class="card-body">
      <div class="card">
      <div class="card-header">
        <ul class="nav nav-tabs card-header-tabs" data-bs-toggle="tabs">
          <li class="nav-item">
            <a href="#tabs-pod" class="nav-link active" data-bs-toggle="tab">Pod</a>
          </li>
          <li class="nav-item">
            <a href="#tabs-deployment" class="nav-link" data-bs-toggle="tab">Deployment</a>
          </li>
          <li class="nav-item">
            <a href="#tabs-service" class="nav-link" data-bs-toggle="tab">Service</a>
          </li>
          <li class="nav-item">
            <a href="#tabs-hpa" class="nav-link" data-bs-toggle="tab">HPA</a>
          </li>
          <li class="nav-item">
            <a href="#tabs-configMap" class="nav-link" data-bs-toggle="tab">ConfigMap</a>
          </li>
        </ul>
      </div>
      <div class="card-body">
        <div class="tab-content">
          <PodForm />
          <DeploymentForm />
          <ServiceForm />
          <HpaForm />
          <ConfigmapForm />
          </div>
        </div>
      </div>
    </div>
  </div>
  
</template>
<script setup lang="ts">
import PodForm from './components/podForm.vue';
import DeploymentForm from './components/deploymentForm.vue';
import ServiceForm from './components/serviceForm.vue';
import HpaForm from './components/hpaForm.vue';
import ConfigmapForm from './components/configmapForm.vue';
import { onMounted } from 'vue';
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
const toast = useToast()


/**
 * @Title Life Cycle
 * @Desc 컬럼 set Callback 함수 호출 / ossList Callback 함수 호출
 */
onMounted(async () => {

})


</script>