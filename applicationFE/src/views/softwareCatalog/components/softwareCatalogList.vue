<template>
  <div ref="sofwareCatalog">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h2 class="mb-0">Catalog</h2>
      <button 
        class="btn btn-outline-primary d-none d-sm-inline-block" 
        style="margin-right: 315px;" 
        data-bs-toggle="modal" 
        data-bs-target="#modal-wizard"
        @click="onClickRegist">
        Regist
      </button>
    </div>
    <!-- Navbar -->
    <div class="row">
      <div class="col-lg-9">
        <div class="card">
          <div class="list-group card-list-group" id="sc-list-group">
            <div 
              class="list-group-item pe-1" 
              v-for="(catalog, idx) in catalogList" 
              :key="idx">
              <div class="row g-2 align-items-center">
                <!-- <div class="col-auto fs-3">{{ idx + 1 }}</div> -->
                <div class="col-auto me-3">
                  <img v-if="catalog.logoUrlLarge" :src="catalog.logoUrlLarge" class="rounded" alt="Catalog Icon" width="40" height="40">
                  <img v-else src="https://artifacthub.io/static/media/placeholder_pkg_helm.png" class="rounded" alt="Catalog Icon" width="40" height="40">
                </div>
                
                <!-- Catalog Name -->
                <div class="col-5" @click="showSoftwareCatalogDetail(idx)">
                  {{ catalog.name }}
                  
                  <!-- Catalog Summary -->
                  <div class="text-muted">
                    {{ catalog.summary }}
                  </div>
                </div>

                <div class="col-3 d-flex justify-content-end"  @click="showSoftwareCatalogDetail(idx)">
                  <span class="text-muted" style="width: auto; text-align: right;">
                    <IconStarFilled class="icon me-1" width="12" height="12" stroke-width="1" color="#e5b942" />
                    <span style="color: #e5b942">
                      {{ catalog.averageRating || 0 }}
                    </span>
                    <span style="color: #e5b942;">
                      ({{ catalog.ratingCount || 0 }})
                    </span>
                  </span>
                  <span class="text-muted" style="width: 80px; text-align: right;">
                    <IconCloudDownload class="icon me-1" width="12" height="12" stroke-width="1" color="gray" />
                    <span style="color: gray;">
                      {{ catalog.downloadCount || 0 }} 
                    </span>
                  </span>
                </div>
                
                <!-- Catalog Category -->
                <div class="col-3 text-muted">
                  <div class="d-flex justify-content-end">
                    <div class="mouse-hover">
                      <IconEdit class="me-2 cursor-pointer" size="15" stroke-width="2" data-bs-toggle="modal" data-bs-target="#modal-wizard" @click="onClickUpdate(catalog.id)" />
                      <IconTrash class="cursor-pointer" size="15" stroke-width="2" @click="onClickDelete(catalog)" />
                    </div>
                  </div>
                  <div class="d-flex justify-content-end"  @click="showSoftwareCatalogDetail(idx)">
                    <span class="text-muted">
                      {{ catalog.category.length > 25 ? catalog.category.substring(0, 25) + "..." : catalog.category}}
                    </span>
                  </div>
                </div>

                <!-- Dots -->
                <!-- <div class="col-auto lh-1">
                  <div class="dropdown">
                    <a href="javascript:void(0);" class="link-secondary" @click="toggleDropdown(`dropdown-${catalog.id}`)">
                      <IconDots class="icon" width="24" height="24" stroke-width="2" />
                    </a>
                    <div :id="`dropdown-${catalog.id}`" class="dropdown-menu dropdown-menu-end" :class="{ 'show': activeDropdown === `dropdown-${catalog.id}` }">
                      <a 
                        class="dropdown-item" 
                        @click="onClickUpdate(catalog.id)" 
                        data-bs-toggle="modal"
                        data-bs-target="#modal-wizard">
                        Update
                      </a>
                      <a 
                        class="dropdown-item" 
                        @click="onClickDelete(catalog)"
                        href="javascript:void(0);">
                        Delete
                      </a>
                    </div>
                  </div>
                </div> -->

                <div 
                  :id="'accordion_' + catalog.id" 
                  class="accordion-collapse collapse"
                  :style="[catalog.isShow ? {display: 'block'} : {display:'none'}]">
                  <div class="accordion-body pt-0">
                    <div 
                      class="mt-3 mb-5" 
                      v-html="formattedText(catalog.description)" />
                      <div>

                        <!-- Ref Information(Hompage) -->
                        <strong>Ref Information</strong>
                        <ul :id="`${idx}-entity-ul`">
                          <template v-if="hasProperty(catalog.refData, 'HOMEPAGE')">
                            <template v-for="(homepage, idx) in catalog.refData.HOMEPAGE" :key="idx">
                              <li>
                                <a 
                                  class="btn"
                                  @click="goToPage(homepage.refValue)" >
                                  {{ homepage.refValue }}
                                </a>
                              </li>  
                            </template>
                          </template>
                        </ul>

                        <!-- Tags -->
                        <strong>TAGS</strong>
                        <ul :id="`${idx}-tag-ul`">
                          <template v-if="hasProperty(catalog.refData, 'TAG')">
                            <template v-for="(tag, idx) in catalog.refData.TAG" :key="idx">
                              <span>#{{ tag.refValue }} &nbsp;</span>
                            </template>
                          </template>
                        </ul>

                        <!-- Recommended Spec -->
                        <strong>Recommended Spec</strong>
                        <ul :id="`${idx}-tag-ul`">
                          <template
                            v-if="catalog.recommendedCpu && catalog.recommendedMemory && catalog.recommendedDisk">
                            <button class="btn btn-sm" style="margin-right: 5px;">
                              CPU : {{ catalog.recommendedCpu }} Core
                            </button>
                            <button class="btn btn-sm" style="margin-right: 5px;">
                              MEMORY : {{ catalog.recommendedMemory }} GB
                            </button>
                            <button class="btn btn-sm" style="margin-right: 5px;">
                              DISK : {{ catalog.recommendedDisk }} GB
                            </button>
                          </template>
                        </ul>
                        <!-- <br />
                        <div class="btn-list" style="width:70%;" v-for="wf in catalog.refData.workflow"
                          :key="wf.catalogRefIdx">
                          <a class="btn"
                            :class="{'btn-outline-primary': containsText('install', wf.referenceValue), 'btn-outline-danger' : containsText('uninstall', wf.referenceValue)}"
                            style="margin-bottom:10px;" @click="onClickDeploy(wf.referenceValue)"
                            data-bs-toggle='modal' data-bs-target='#install-form'>
                            {{ btnName(wf.referenceValue) }}
                          </a>
                          {{ wf.referenceValue }}
                          <button class="btn btn-primary" style="text-align: center !important; margin-bottom:10px;"
                            @click="onClickLog(wf.referenceValue)" id='log-btn' data-bs-toggle='modal'
                            data-bs-target='#softwareCatalogLog'>
                            &nbsp;LOG&nbsp;
                          </button>
                        </div> -->
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Search Area -->
      <div class="col-lg-3">
        <div class="input-icon mb-3">
          <span class="input-icon-addon">
            <IconSearch class="icon" width="24" height="24" stroke-width="2" />
          </span>
          <input 
            type="text" 
            class="form-control" 
            placeholder="Search…" 
            @keypress="searchCatalog"
            v-model="searchKeyword" 
            id="inputCatalogSearch">
        </div>
        
        <!-- docker hub Search -->
        <h3 class="mb-3">
         DOCKERHUB
        </h3>
        <div 
          v-if="dockerHubSearchList.length <= 0"
          class="col-md-6 col-lg-12" 
          id="resultDockerHubEmpty">
          There are no related Container Images found.
        </div>

        <div v-if="dockerHubSearchList.length > 0" class="row row-cards" id="resultDockerHubSearch">
          <div 
            v-for="(result, idx) in dockerHubSearchList" 
            class="col-md-6 col-lg-12" 
            :key="idx">
            <div class="card">
              <div class="row row-0">
                <div class="col-auto">
                  <img 
                    :src="result.logo_url.large" 
                    class="rounded-start ms-2" 
                    alt="Shape of You" 
                    width="80"
                    height="80">
                </div>
                <div class="col">
                  <div class="card-body">
                    <a :href="'https://hub.docker.com/search?q=' + searchKeyword" target="_blank">
                      {{result?.name}}
                    </a>
                    <div class="text-muted">
                      {{ result?.short_description.length > 30 ? result?.short_description.substring(0, 30) + "..." : ""}}
                    </div>
                  </div>
                </div>
                <div class="col-auto lh-1">
                  <div class="d-flex justify-content-end me-2 mt-4 mouse-hover">
                    <IconDownload class="cursor-pointer" size="20" stroke-width="2" @click="onClickUpload(result, 'DockerHub')" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="mt-5">
          <h3 class="mb-3">
            ARTIFACTHUB 
          </h3>
          <div 
            v-if="artifactHubSearchList.length <= 0"
            class="col-md-6 col-lg-12" 
            id="resultArtifactHubEmpty">
            There are no related Helm Charts found.
          </div>

          <div 
            v-if="artifactHubSearchList.length > 0"
            class="row row-cards" 
            id="resultArtifactHubSearch">
            <div 
              v-for="(result, idx) in artifactHubSearchList" 
              class="col-md-6 col-lg-12" 
              :key="idx">
              <div class="card">
                <div class="row row-0">
                  <div class="col-auto">
                    <img src="https://artifacthub.io/static/media/placeholder_pkg_helm.png" class="rounded-start" alt="Shape of You" width="80" height="80">
                  </div>
                  <div class="col">
                    <div class="card-body">
                      <a :href="'https://artifacthub.io/packages/search?ts_query_web=' + searchKeyword + '&sort=relevance&page=1'" target="_blank">
                        {{result?.name}}
                      </a>
                      <div class="text-muted">
                        {{result?.description.length > 30 ? result?.description.substring(0, 30) + "..." : ""}}
                      </div>
                    </div>
                  </div>
                  <div class="col-auto lh-1">
                    <div class="d-flex justify-content-end me-2 mt-4 mouse-hover">
                      <IconDownload class="cursor-pointer" size="20" stroke-width="2" @click="onClickUpload(result, 'ArtifactHub')" />
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
  
  <DeleteConfirmModal 
    ref="deleteConfirmModal"
    :target-catalog="deleteTargetCatalog"
    @deleted="onCatalogDeleted"
    @close="onDeleteModalClose" />
    
  <SoftwareCatalogWizard 
    ref="wizardModal"
    :mode="wizardMode"
    @created="_getSoftwareCatalogList"
    @updated="_getSoftwareCatalogList" />
    
  <UploadForm 
    ref="uploadFormModal"
    :source-data="uploadData"
    @uploaded="onUploaded"
    @close="onUploadModalClose" />
    
  <!-- <SoftwareCatalogForm 
    :mode="formMode" 
    :catalog-idx="selectCatalogIdx" 
    :repository-application-info="repositoryApplicationInfo"
    :repository-name="repositoryName"
    @get-list="_getSoftwareCatalogList" /> -->
</template>
<script setup lang="ts">
// Component
import { IconDots, IconDownload, IconEdit, IconRowRemove, IconSearch, IconTrash, IconUpload, IconStar, IconCloud, IconCloudFilled, IconStarFilled, IconCloudDown, IconCloudDownload } from '@tabler/icons-vue'
// @ts-ignore
import SoftwareCatalogForm from './softwareCatalogForm.vue';
// @ts-ignore
import SoftwareCatalogWizard from './softwareCatalogWizard.vue';
// @ts-ignore
import DeleteConfirmModal from './DeleteConfirmModal.vue';
// @ts-ignore
import UploadForm from './uploadForm.vue';

// API
import { getSoftwareCatalogList, searchArtifacthubhub, searchDockerhub, upLoadDockerHubApplication, upLoadArtifactHubApplication } from '../../../api/softwareCatalog';

// ETC
import { computed, onMounted, ref } from 'vue';
import type { SoftwareCatalog } from '../../type/type';
import { useToast } from 'vue-toastification';
// @ts-ignore
import _ from 'lodash';

const toast = useToast()

const catalogList = ref([] as Array<SoftwareCatalog | any>)
const selectCatalogIdx = ref(null as number | null)
const selectCatalogId = ref(null as number | null)
const selectCatalogInfo = ref({} as any)

// const formMode = ref('new')
const wizardMode = ref('new')
const repositoryApplicationInfo = ref({} as any)
const repositoryName = ref("" as string)

// 삭제 관련 상태
const deleteTargetCatalog = ref({} as any)
const deleteConfirmModal = ref<any>(null)

// 업로드 관련 상태
const uploadFormModal = ref<any>(null)
const uploadData = ref({} as any)

// 위저드 모달 관련 상태
const wizardModal = ref<any>(null)

const searchKeyword = ref("")
const dockerHubSearchList = ref([] as any)
const artifactHubSearchList = ref([] as any)

// Dropdown 제어를 위한 상태
const activeDropdown = ref("")

/**
 * @Title Dropdown 제어
 * @Desc dropdown을 토글하는 메서드
 */
const toggleDropdown = (dropdownId: string) => {
  if (activeDropdown.value === dropdownId) {
    activeDropdown.value = ""
  } else {
    activeDropdown.value = dropdownId
  }
}

/**
 * @Title Life Cycle
 * @Desc SearchKeyword 변수 초기화 / catalogList set Method call
 */
onMounted(async () => {
  searchKeyword.value = ""
  _getSoftwareCatalogList()
  
  // 클릭 이벤트 리스너 추가 (dropdown 외부 클릭 시 닫기)
  document.addEventListener('click', (e) => {
    const target = e.target as HTMLElement
    if (!target.closest('.dropdown')) {
      activeDropdown.value = ""
    }
  })
})

const onClickRegist = () => {
  _getSoftwareCatalogList()

  wizardMode.value = 'new'
  selectCatalogId.value = null
  selectCatalogInfo.value = {}
  selectCatalogIdx.value = 0;
  repositoryApplicationInfo.value = {}
  repositoryName.value = ""
  
  // 모달이 열린 후 초기화
  setTimeout(() => {
    if (wizardModal.value && typeof wizardModal.value.initForCreate === 'function') {
      wizardModal.value.initForCreate()
    }
  }, 100)
}

/**
* @Method _getSoftwareCatalogList
* @Desc software catalog List get Method Call / set Data
*/
const _getSoftwareCatalogList = async () => {
  try {
    await getSoftwareCatalogList(searchKeyword.value).then(({ data }) => {
      _.forEach(data, function(item: {
        isShow: boolean;
        refData: any;
        catalogRefs: any;
      }) {
        item.refData = groupedData(item.catalogRefs)
        item.isShow = false;
      })
      catalogList.value = data;
    })
  } catch(error) {
    console.log(error)
    toast.error('Unable to retrieve data.')
  }
}

const groupedData = (catalogRefs: any) => {
  return catalogRefs.reduce((acc:any, item:any) => {
    if (!acc[item.refType]) {
      acc[item.refType] = [];
    }
    acc[item.refType].push(item);
    return acc;
  }, {});
}

/**
* @Method searchCatalog
* @Desc public repository(dockerHub / artifactHub) API Call / data set
*/
const searchCatalog = async (e: { keyCode: number; }) => {
  // trigger :: press enter key
  if(e.keyCode == 13){
    await setDockerHubSearchList();
    await setArtifactHubSearchList();
  }
}

/**
* @Method setDockerHubSearchList
* @Desc dockerHub API Call / data set
*/
const setDockerHubSearchList = async () => {
  dockerHubSearchList.value  = [];
  try {
    const { data } = await searchDockerhub(searchKeyword.value)
    
    if(data.results.length > 0) {
      for(let i=0; i<3; i++) {
        dockerHubSearchList.value.push(data.results[i])
      }
    }
  } catch(error) {
    console.log(error)
    toast.error('Unable to retrieve data.')
  }
}

/**
* @Method setArtifactHubSearchList
* @Desc artifactHub API Call / data set
*/
const setArtifactHubSearchList = async () => {
  artifactHubSearchList.value = [];
  try {
    const { data } = await searchArtifacthubhub(searchKeyword.value)
    if(data.packages.length > 0) {
      for(let i=0; i<3; i++) {
        artifactHubSearchList.value.push(data.packages[i])
      }
    }
  } catch(error) {
    console.log(error)
    toast.error('Unable to retrieve data.')
  }
}

/**
* @Method onClickCreate
* @Desc Regist SoftwareCatalog Popup set
*/
const onClickCreate = (repoName: string, result: any) => {
  // formMode.value = 'new'
  selectCatalogIdx.value = 0;
  repositoryApplicationInfo.value = result
  repositoryName.value = repoName
}

/**
* @Method onClickUpdate
* @Desc Update SoftwareCatalog Popup set
*/
const onClickUpdate = (catalogId: number) => {
  // 선택된 catalog 찾기
  const selectedCatalog = catalogList.value.find(catalog => catalog.id === catalogId)
  
  selectCatalogId.value = catalogId
  selectCatalogInfo.value = selectedCatalog || {}
  wizardMode.value = 'update'
  
  // 모달이 열린 후 업데이트용 초기화
  setTimeout(() => {
    if (wizardModal.value && typeof wizardModal.value.initForUpdate === 'function') {
      wizardModal.value.initForUpdate(catalogId, selectedCatalog)
    }
  }, 100)
}

const onClickDelete = (catalog: any) => {
  deleteTargetCatalog.value = catalog
  
  // 모달 컴포넌트 열기
  if (deleteConfirmModal.value) {
    deleteConfirmModal.value.show()
  }
}

// 삭제 완료 이벤트 핸들러
const onCatalogDeleted = async (catalogId: number) => {
  // 목록 새로고침
  await _getSoftwareCatalogList()
}

// 모달 닫기 이벤트 핸들러
const onDeleteModalClose = () => {
  deleteTargetCatalog.value = {}
}

/**
* @Method onClickMovePageDockerHub
* @Desc Move the page to Docker Hub
*/
const onClickMovePageDockerHub = () => {
  let dockerHubUrl = `https://hub.docker.com/search?q=${searchKeyword.value}`;
  window.open(dockerHubUrl, '_blank');
}

/**
* @Method onClickMovePageArtifactHub
* @Desc Move the page to Artifact Hub
*/
const onClickMovePageArtifactHub = () => {
  let artifactHubUrl = `https://artifacthub.io/packages/search?ts_query_web=${searchKeyword.value}&sort=relevance&page=1`;
  window.open(artifactHubUrl, '_blank');
}

const showSoftwareCatalogDetail = async (idx:any) => {
  catalogList.value[idx].isShow = !catalogList.value[idx].isShow
}

const hasProperty = (data:any, prop:any) => {
  return Object.prototype.hasOwnProperty.call(data, prop);
}

const goToPage = (url:string) => {
  window.open(url)
}

const formattedText = (text:string) => {
  return text.replace(/\\n|\n/g, '<br/>');
}

/**
* @Method onClickUpload
* @Desc Upload 팝업 열기 및 데이터 설정
*/
const onClickUpload = (sourceData: any, sourceType: string) => {
  
  // 업로드 데이터 설정
  uploadData.value = sourceData
  uploadData.value.sourceType = sourceType
  
  // 업로드 폼 모달 열기
  if (uploadFormModal.value) {
    uploadFormModal.value.show()
  }
}

/**
* @Method onUploaded
* @Desc 업로드 완료 이벤트 핸들러
*/
const onUploaded = async (uploadFormData: any) => {
  if(uploadFormData.sourceType == 'DockerHub') {
    // Backend에 맞게 데이터 변환
    uploadFormData.createdAt = uploadFormData.created_at
    uploadFormData.updatedAt = uploadFormData.updated_at
    uploadFormData.shortDescription = uploadFormData.short_description
    uploadFormData.starCount = uploadFormData.star_count
    uploadFormData.ratePlans = uploadFormData.rate_plans

    delete uploadFormData.created_at
    delete uploadFormData.updated_at
    delete uploadFormData.short_description
    delete uploadFormData.star_count
    delete uploadFormData.rate_plans


    const { data } = await upLoadDockerHubApplication(uploadFormData)
  } else if(uploadFormData.sourceType == 'ArtifactHub') {
    const { data } = await upLoadArtifactHubApplication(uploadFormData)
  }

  toast.success('Software catalog uploaded successfully!')
  
  // 업로드 완료 후 처리 로직 추가 (API 호출 등)
  // TODO: 실제 업로드 API 호출
}

/**
* @Method onUploadModalClose
* @Desc 업로드 모달 닫기 이벤트 핸들러
*/
const onUploadModalClose = () => {
  uploadData.value = {
    sourceType: '',
    name: '',
    sourceData: {}
  }
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

/* .me-3 {
  margin-right: 3px;
} */
 .mouse-hover {
   opacity: 0;
   transition: opacity 0.15s ease-in-out;
 }
 .list-group-item:hover .mouse-hover {
   opacity: 1;
 }
 #resultDockerHubSearch .card:hover .mouse-hover {
   opacity: 1;
 }
 #resultArtifactHubSearch .card:hover .mouse-hover {
   opacity: 1;
 }
</style>