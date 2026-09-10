<template>
  <div class="page" ref="sofwareCatalog">
    <!-- Navbar -->
    <div class="page-wrapper">
      <!-- Page header -->
      <div class="page-header d-print-none">
        <div class="container-xxl">
          <div class="row g-2 align-items-center">
            <!-- Title -->
            <div class="col d-flex">
              <h2 class="page-title">Software Catalog</h2>
            </div>
            <!-- Catalog management actions -->
            <div class="col-auto ms-auto">
              <div class="btn-list">
                <button
                  type="button"
                  class="btn btn-outline-primary page-header-action-btn"
                  @click="onClickRegister">
                  Register
                </button>
                <button
                  type="button"
                  class="btn btn-primary page-header-action-btn"
                  @click="onClickDeploy('Application Installation')">
                  Deploy
                </button>
              </div>
            </div>
            <!-- New Button -->
            <!-- 
              TODO : 2024-11-14 작업
              Desc : New 버튼이 목록에서 사라지고 우측 public Repository 에서 검색후 새로운 카탈로그 등록으로 변경 
            -->
            <!-- <div class="col-auto ms-auto">
              <div class="btn-list">
                <a 
                  class="btn btn-primary d-none d-sm-inline-block" 
                  @click="onClickCreate" 
                  data-bs-toggle="modal"
                  data-bs-target="#modal-form">
                  <IconPlus 
                    class="icon icon-tabler icon-tabler-plus" 
                    width="24" 
                    height="24" 
                    stroke-width="2" />
                  New
                </a>
              </div>
            </div> -->
          </div>
        </div>
      </div>

      <!-- Page body -->
      <div class="page-body">
        <div class="container-xxl">
          <div class="row">
            <div class="col-lg-12">
              <SoftwareCatalogList ref="softwareCatalogListRef" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <ApplicationInstallationForm
    :ns-id="nsId" 
    :title="modalTite" />
</template>
<script setup lang="ts">
// Components
import ApplicationInstallationForm from '@/views/softwareCatalog/components/applicationInstallationForm.vue';
import SoftwareCatalogList from '@/views/softwareCatalog/components/softwareCatalogList.vue';
import { Modal } from 'bootstrap';

// ETC
import { computed, nextTick, ref } from 'vue';
import { useUserStore } from '@/stores/user'

const userinfo = useUserStore();
const nsId = computed(() => userinfo.getNsId() || '')
const modalTite = ref("" as string)
const softwareCatalogListRef = ref<InstanceType<typeof SoftwareCatalogList> | null>(null)

const openModal = (modalId: string) => {
  const modalElement = document.getElementById(modalId)
  if (modalElement) {
    Modal.getOrCreateInstance(modalElement).show()
  }
}

/**
* @Method onClickDeploy
* @Desc If when you Application Install or Uninstall Action
*/
const onClickDeploy = async (value: string) => {
  modalTite.value = value
  await nextTick()
  openModal('install-form')
}

const onClickRegister = async () => {
  softwareCatalogListRef.value?.startRegistration()
  await nextTick()
  openModal('modal-wizard')
}
</script>
<style>
@import url('https://rsms.me/inter/inter.css');
:root {
  --tblr-font-sans-serif: 'Inter Var', -apple-system, BlinkMacSystemFont, San Francisco, Segoe UI, Roboto, Helvetica Neue, sans-serif;
}
body {
  font-feature-settings: "cv03", "cv04", "cv11";
}

.btn-grid-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-column-gap: 10px;
  grid-row-gap: 10px;
}
</style>
