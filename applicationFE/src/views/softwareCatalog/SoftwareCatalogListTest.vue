<template>
  <div class="page" ref="sofwareCatalog">
    <!-- Navbar -->
    <div class="page-wrapper">
      <!-- Page header -->
      <div class="page-header d-print-none">
        <div class="container-xl">
          <div class="row g-2 align-items-center">
            <div class="col d-flex">
              <h2 class="page-title">Software catalog</h2>
              <!-- <button 
                class="btn btn-success m-3 btn-sm" 
                @click="onClickStatus" 
                data-bs-toggle="modal"
                data-bs-target="#status-modal">STATUS</button> -->
            </div>

            <div class="col-auto ms-auto d-print-none">
              <div class="d-flex">
                <div class="me-3 d-none d-md-block">
                  <div class="input-icon">
                    <input type="text" class="form-control" placeholder="Search…" />
                    <span class="input-icon-addon">
                      <IconSearch  class="icon icon-tabler icon-tabler-search" :size="24"></IconSearch>
                    </span>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="col-auto ms-auto">
              <div class="btn-list">
                <a 
                  class="btn btn-primary d-none d-sm-inline-block" 
                  @click="onClickCreate" 
                  data-bs-toggle="modal"
                  data-bs-target="#modal-form">
                  <IconPlus 
                    class="icon icon-tabler icon-tabler-plus" 
                    color="white" 
                    :size="20" 
                    stroke-width="2" />
                  New
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Page body -->
      <div class="page-body">
        <div class="container-xl">
          <div class="row">
            <div class="col-auto">
              <div class="card bg-color-white">
                <div class="card-body">
                  <div class="row row-deck">
                    <div class="col-md-4" v-for="(catalogInfo, idx) in catalogList" :key="idx" style="width: 33%; display: flex; justify-content: space-between; ">
                      <div class="w-100">
                        <softwareCatalogCard 
                          :catalog-info="catalogInfo"
                          :idx="idx"
                          data-bs-toggle='modal' 
                          data-bs-target='#software-card-detail'
                          @setSoftwareCatalogRefrence="setSoftwareCatalogRefrence(idx)" 
                        />
                      </div> 
                    </div>
                    <!-- <div class="card d-flex flex-column" v-for="(catalog, idx) in catalogList" :key="idx" style="width: 33%;"> 
                      {{ catalog.catalogTitle }}
                    </div> -->
                  </div>
                </div>
              </div>
            </div>
            <!-- <div class="col-lg-4">
              <input type="text" class="form-control" placeholder="Search…" @keypress="searchCatalog"
                v-model="searchKeyword" id="inputCatalogSearch">
              <div> <br /></div>
              <h3 class="mb-3">dockerHub search</h3>
              <div class="col-md-6 col-lg-12" id="resultDockerHubEmpty" v-if="dockerHubSearchList.length == 0">검색된 관련
                ContainerImage가 없습니다.</div>
              <div class="row row-cards" id="resultDockerHubSearch">
                < !-- <div class="progress progress-sm"> <div class="progress-bar progress-bar-indeterminate"></div> </div> -- >
                <div class="col-md-6 col-lg-12" v-for="(result, idx) in dockerHubSearchList" :key="idx">
                  <div class="card">
                    <div class="row row-0">
                      <div class="col-auto">
                        <img :src="result.logo_url.large" class="rounded-start" alt="Shape of You" width="80"
                          height="80">
                      </div>
                      <div class="col">
                        <div class="card-body">
                          <a href="" target="_blank">{{result.name}}</a>
                          <div class="text-muted">
                            {{result.short_description.length > 30 ? result.short_description.substring(0, 30) + "..." :
                            ""}}
                          </div>
                        </div>
                      </div>
                      <div class="col-auto lh-1">
                        <div class="dropdown">
                          <a href="#" class="link-secondary" data-bs-toggle="dropdown">
                            <svg xmlns="http://www.w3.org/2000/svg" class="icon" width="24" height="24"
                              viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" fill="none"
                              stroke-linecap="round" stroke-linejoin="round">
                              <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                              <path d="M5 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" />
                              <path d="M12 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" />
                              <path d="M19 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" />
                            </svg>
                          </a>
                          <div class="dropdown-menu dropdown-menu-end">
                            <a class="dropdown-item" @click="onClickDockerHubSearch">
                              해당 페이지로 이동
                            </a>
                            <a class="dropdown-item" href="#">
                              softwareCatalog로 내용 입력
                            </a>
                            <a class="dropdown-item" href="#">
                              file/image를 nexus로 복제
                            </a>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <div style="margin-bottom:20px;">&nbsp;</div>
              <h3 class="mb-3">artifactHub search</h3>
              <div class="col-md-6 col-lg-12" id="resultArtifactHubEmpty" v-if="artifactHubSearch.length == 0">검색된 관련
                HelmChart가 없습니다.</div>
              <div class="row row-cards" id="resultArtifactHubSearch">
                < !-- <div class="progress progress-sm"> <div class="progress-bar progress-bar-indeterminate"></div> </div> -- >
                <div class="col-md-6 col-lg-12" v-for="(result, idx) in artifactHubSearch" :key="idx">
                  <div class="card">
                    <div class="row row-0">
                      <div class="col-auto">
                        <img src="https://artifacthub.io/static/media/placeholder_pkg_helm.png" class="rounded-start"
                          alt="Shape of You" width="80" height="80">
                      </div>
                      <div class="col">
                        <div class="card-body">
                          <a href="" target="_blank">{{result.name}}</a>
                          <div class="text-muted">
                            {{result.description.length > 30 ? result.description.substring(0, 30) + "..." : ""}}
                          </div>
                        </div>
                      </div>
                      <div class="col-auto lh-1">
                        <div class="dropdown">
                          <a href="#" class="link-secondary" data-bs-toggle="dropdown">
                            <svg xmlns="http://www.w3.org/2000/svg" class="icon" width="24" height="24"
                              viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" fill="none"
                              stroke-linecap="round" stroke-linejoin="round">
                              <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                              <path d="M5 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" />
                              <path d="M12 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" />
                              <path d="M19 12m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0" />
                            </svg>
                          </a>
                          <div class="dropdown-menu dropdown-menu-end">
                            <a class="dropdown-item" @click="onClickArtifactHub">
                              해당 페이지로 이동
                            </a>
                            <a class="dropdown-item" href="#">
                              softwareCatalog로 내용 입력
                            </a>
                            <a class="dropdown-item" href="#">
                              file/image를 nexus로 복제
                            </a>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div> -->
          </div>
        </div>
      </div>
    </div>
    <!-- <SoftwareStatus :ns-name="nsName" /> -->
    <SoftwareCatalogForm :mode="formMode" :catalog-idx="selectCatalogIdx+1" @get-list="_getSoftwareCatalogList" />
    <SoftwareCatalogLog :job-name="selectJobName" />
    <ApplicationInstallationForm 
      :ns-id="nsId" 
      :title="modalTite" 
      :catalog-list="catalogList"/>
    <SoftwareCatalogCardDetail
      :catalog-idx="selectCatalogIdx+1"
      @onClickDeploy="onClickDeploy"
    />
  </div>
</template>
<script setup lang="ts">
import { IconPlus, IconSearch } from '@tabler/icons-vue';
import softwareCatalogCard from '@/views/softwareCatalog/components/softwareCatalogCard.vue'
import SoftwareCatalogCardDetail from '@/views/softwareCatalog/components/softwareCatalogCardDetail.vue'

import { onMounted } from 'vue';
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
// @ts-ignore
import _ from 'lodash';
import SoftwareCatalogForm from './components/softwareCatalogForm.vue';
import SoftwareCatalogLog from './components/softwareCatalogLog.vue';
import ApplicationInstallationForm from './components/applicationInstallationForm.vue';
import SoftwareStatus from './components/softwareStatus_back.vue';
import '@/resources/css/tabler.min.css'
import '@/resources/css/demo.min.css'
import '@/resources/js/demo-theme.min.js'
import { getSoftwareCatalogList, searchArtifacthubhub, searchDockerhub } from '@/api/softwareCatalog';
  
const toast = useToast()

const catalogList = ref([] as any)
const searchKeyword = ref("")


const dockerHubSearchList = ref([] as any)
const artifactHubSearch = ref([] as any)
const selectCatalogIdx = ref(0 as number)
const selectJobName = ref("" as string)
const formMode = ref('new')
const nsId = ref("" as string)
const nsName = ref("ns01" as string)
const modalTite = ref("" as string)

/**
* @Title Life Cycle
* @Desc 컬럼 set Callback 함수 호출 / catalogList Callback 함수 호출
*/
onMounted(async () => {
  searchKeyword.value = ""
  window.addEventListener("message", async function(event) {
    const data = event.data;
    if (data.projectInfo) {
      nsId.value = data.projectInfo.ns_id;
    }
  })
  await _getSoftwareCatalogList()
})

/**
* @Title _getSoftwareCatalogList
* @Desc software catalog List Callback 함수 / software catalog List api 호출
*/
const _getSoftwareCatalogList = async () => {
  try {
    const response = await getSoftwareCatalogList(searchKeyword.value)
    _.forEach(response.data, function(item: {
      isShow: boolean;
      refData: any;
      catalogRefData: any; catalogIcon: string; 
    }) {
      const splitUrl = window.location.host.split(':');
      const baseUrl = window.location.protocol + '//' + splitUrl[0] + ':18084'

      item.catalogIcon = baseUrl + item.catalogIcon
      item.refData = groupedData(item.catalogRefData)
      item.isShow = false;
    })
    catalogList.value = response.data;
  } catch(error) {
    console.log(error)
    toast.error('데이터를 가져올 수 없습니다.')
  }
}

const groupedData = (catalogRefData: any) => {
  return catalogRefData.reduce((acc:any, item:any) => {
    if (!acc[item.referenceType]) {
      acc[item.referenceType] = [];
    }
    acc[item.referenceType].push(item);
    return acc;
  }, {});
}

const searchCatalog = async (e: { keyCode: number; }) => {
  if(e.keyCode == 13){
    await _getSoftwareCatalogList();
    await setDockerHubSearch();
    await setArtifactHubSearch();
  }
}

const setDockerHubSearch = async () => {
  dockerHubSearchList.value  = [];
  try {
    const response = await searchDockerhub(searchKeyword.value)
    
    for(let i=0; i<3; i++) {
      dockerHubSearchList.value.push(response.data.data.results[i])
    }
    // dockerHubSearchList.value = response.data.data.results
  } catch(error) {
    console.log(error)
    toast.error('데이터를 가져올 수 없습니다.')
  }
}

const setArtifactHubSearch = async () => {
  artifactHubSearch.value = [];
  try {
    const response = await searchArtifacthubhub(searchKeyword.value)
    for(let i=0; i<3; i++) {
      artifactHubSearch.value.push(response.data.data.packages[i])
    }
    // artifactHubSearch.value = response.data.data.packages
  } catch(error) {
    console.log(error)
    toast.error('데이터를 가져올 수 없습니다.')
  }
}

const setSoftwareCatalogRefrence = async (idx:any) => {
  selectCatalogIdx.value = idx;
  

  catalogList.value.forEach((catalogInfo:any) => {
    catalogInfo.isShow = false
  })
  catalogList.value[idx].isShow = !catalogList.value[idx].isShow
  // try {
  //     const response = await axios.get(baseUrl + '/catalog/software/' + idx);
  //     console.log("response : ", response)
  // } catch(error) {
  //     console.log(error)
  //     toast.error('데이터를 가져올 수 없습니다.')
  // }
}

const hasProperty = (data:any, prop:any) => {
  return Object.prototype.hasOwnProperty.call(data, prop);
}

const goToPage = (url:string) => {
  window.open(url)
}

const onClickUpdate = (idx:number) => {
  formMode.value = "update";
  selectCatalogIdx.value = idx;
}

const onClickCreate = () => {
  formMode.value = "new"
  selectCatalogIdx.value = 0;
}

const onClickDockerHubSearch = () => {
  let dockerHubUrl = `https://hub.docker.com/search?q=${searchKeyword.value}`;
  window.open(dockerHubUrl, '_blank');
}

const onClickArtifactHub = () => {
  let artifactHubUrl = `https://artifacthub.io/packages/search?ts_query_web=${searchKeyword.value}&sort=relevance&page=1`;
  window.open(artifactHubUrl, '_blank');
}

const containsText = (text: any, refVal: string | any[]) => {
  return refVal.includes(text);
}

const btnName = (text: string) => {
  return text.split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
}

const onClickLog = (name: string) => {
  selectJobName.value = name;
}

const onClickDeploy = (value: string) => {
  modalTite.value = value
}

const formattedText = (text:string) => {
  return text.replace(/\\n|\n/g, '<br/>');
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