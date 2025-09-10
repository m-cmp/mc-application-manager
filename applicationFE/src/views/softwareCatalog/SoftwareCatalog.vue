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
            <!-- Install Button relocated into header -->
            <div class="col-auto ms-auto">
              <button 
                class="btn btn-primary" 
                data-bs-toggle='modal' 
                data-bs-target='#install-form'
                @click="onClickDeploy('Application Installation')">
                INSTALL
              </button>
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
              <!-- INSTALL button moved to header; body space gained -->

              <div class="card">

                <!-- Tab Title -->
                <div class="card-header">
                  <ul class="nav nav-tabs card-header-tabs" data-bs-toggle="tabs">

                    <!-- Catalog -->
                    <li class="nav-item">
                      <a href="#tabs-catalog" class="nav-link active" data-bs-toggle="tab">
                      <IconApps class="icon me-2" width="24" height="24" stroke-width="2" /> 
                      Catalog
                    </a>
                    </li>

                    <!-- Status -->
                    <li class="nav-item">
                      <a href="#tabs-status" class="nav-link" data-bs-toggle="tab">
                        <IconActivityHeartbeat class="icon me-2" width="24" height="24" stroke-width="2" /> 
                        Apps Status
                      </a>
                    </li>

                    <!-- Repository -->
                    <li class="nav-item">
                      <a href="#tabs-repository" class="nav-link" data-bs-toggle="tab">
                        <IconFolder class="icon me-2" width="24" height="24" stroke-width="2" /> 
                        Repository
                      </a>
                    </li>
                  </ul>
                </div>

                <!-- Tab Body -->
                <div class="card-body">
                  <div class="tab-content">

                    <!-- Catalog -->
                    <div class="tab-pane active show" id="tabs-catalog">
                      <div>
                        <SoftwareCatalogList :nsId="nsId"/>
                      </div>
                    </div>

                    <!-- Status -->
                    <div class="tab-pane" id="tabs-status">
                      <div>
                        <ApplicationStatusList />
                      </div>
                    </div>

                    <!-- Repository -->
                    <div class="tab-pane" id="tabs-repository">
                      <div>
                        <RepositoryList />
                      </div>
                    </div>
                  </div>
                </div>
              </div>
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
import { IconActivityHeartbeat, IconApps, IconFolder } from '@tabler/icons-vue'
import ApplicationInstallationForm from '@/views/softwareCatalog/components/applicationInstallationForm.vue';
import ApplicationStatusList from '@/views/softwareCatalog/components/applicationStatusList.vue';
import SoftwareCatalogList from '@/views/softwareCatalog/components/softwareCatalogList.vue';
import RepositoryList from '@/views/repository/RepositoryList.vue';

// ETC
import { onMounted } from 'vue';
import { ref } from 'vue';
import { useUserStore } from '@/stores/user'

// @ts-ignore
import _ from 'lodash';

const userinfo = useUserStore();
const nsId = ref("" as string)
const modalTite = ref("" as string)

/**
* @Title Life Cycle
* @Desc 컬럼 set Callback 함수 호출
*/
onMounted(async () => {
  nsId.value = userinfo.getNsId();
})


/**
* @Method onClickDeploy
* @Desc If when you Application Install or Uninstall Action
*/
const onClickDeploy = (value: string) => {
  modalTite.value = value
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