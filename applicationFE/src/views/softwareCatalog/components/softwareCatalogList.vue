<template>
  <div ref="sofwareCatalog">
    <h2>Catalog</h2>
    <!-- Navbar -->
    <div class="row">
      <div class="col-lg-9">
        <div class="card">
          <div class="list-group card-list-group" id="sc-list-group">
            <div 
              class="list-group-item" 
              v-for="(catalog, idx) in catalogList" 
              :key="idx">
              <div class="row g-2 align-items-center">
                <!-- <div class="col-auto fs-3">{{ idx + 1 }}</div> -->
                <div class="col-auto me-3">
                  <img :src="catalog.logoUrlLarge" class="rounded" alt="Catalog Icon" width="40" height="40">
                </div>
                
                <!-- Catalog Title -->
                <div class="col" @click="showSoftwareCatalogDetail(idx)">
                  {{ catalog.title }}
                  
                  <!-- Catalog Summary -->
                  <div class="text-muted">
                    {{ catalog.summary }}
                  </div>
                </div>
                
                <!-- Catalog Category -->
                <div class="col-auto text-muted">
                  {{ catalog.category }}
                </div>

                <!-- Dots -->
                <div class="col-auto lh-1">
                  <div class="dropdown">
                    <a href="#" class="link-secondary" data-bs-toggle="dropdown">
                      <IconDots class="icon" width="24" height="24" stroke-width="2" />
                    </a>
                    <div class="dropdown-menu dropdown-menu-end">
                      <a 
                        class="dropdown-item" 
                        @click="onClickUpdate(catalog.id)" 
                        data-bs-toggle="modal"
                        data-bs-target="#modal-form">
                        Update
                      </a>
                    </div>
                  </div>
                </div>

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
                              CPU : {{ catalog.recommendedCpu }}
                            </button>
                            <button class="btn btn-sm" style="margin-right: 5px;">
                              MEMORY : {{ catalog.recommendedMemory }}
                            </button>
                            <button class="btn btn-sm" style="margin-right: 5px;">
                              DISK : {{ catalog.recommendedDisk }}
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
        <input 
          type="text" 
          class="form-control mb-3" 
          placeholder="Search…" 
          @keypress="searchCatalog"
          v-model="searchKeyword" 
          id="inputCatalogSearch">
        
        <!-- docker hub Search -->
        <h3 class="mb-3">
          dockerHub search
        </h3>
        <div 
          v-if="dockerHubSearchList.length == 0"
          class="col-md-6 col-lg-12" 
          id="resultDockerHubEmpty">
          There are no related Container Images found.
        </div>

        <div class="row row-cards" id="resultDockerHubSearch">
          <div 
            v-for="(result, idx) in dockerHubSearchList" 
            class="col-md-6 col-lg-12" 
            :key="idx">
            <div class="card">
              <div class="row row-0">
                <div class="col-auto">
                  <img 
                    :src="result.logo_url.large" 
                    class="rounded-start" 
                    alt="Shape of You" 
                    width="80"
                    height="80">
                </div>
                <div class="col">
                  <div class="card-body">
                    <a href="" target="_blank">
                      {{result.name}}
                    </a>
                    <div class="text-muted">
                      {{ result.short_description.length > 30 ? result.short_description.substring(0, 30) + "..." : ""}}
                    </div>
                  </div>
                </div>
                <div class="col-auto lh-1">
                  <div class="dropdown">
                    <a 
                      href="#" 
                      class="link-secondary" 
                      data-bs-toggle="dropdown">
                      <IconDots 
                        class="icon" 
                        size="24"
                        stroke-width="2"/>
                    </a>
                    <div class="dropdown-menu dropdown-menu-end">
                      <a 
                        class="dropdown-item" 
                        @click="onClickMovePageDockerHub">
                        Go to the page
                      </a>
                      <a 
                        class="dropdown-item" 
                        href="#" 
                        data-bs-toggle="modal"
                        data-bs-target="#modal-form"
                        @click="onClickCreate('dockerhub', result)" >
                        Enter content into softwareCatalog
                      </a>
                      <a class="dropdown-item" href="#">
                        Copy file/image to repository
                      </a>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="mt-5">
          <h3 class="mb-3">
            artifactHub search
          </h3>
          <div 
            v-if="artifactHubSearchList.length == 0"
            class="col-md-6 col-lg-12" 
            id="resultArtifactHubEmpty">
            There are no related Helm Charts found.
          </div>

          <div 
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
                      <a href="" target="_blank">
                        {{result.name}}
                      </a>
                      <div class="text-muted">
                        {{result.description.length > 30 ? result.description.substring(0, 30) + "..." : ""}}
                      </div>
                    </div>
                  </div>
                  <div class="col-auto lh-1">
                    <div class="dropdown">
                      <a href="#" class="link-secondary" data-bs-toggle="dropdown">
                        <IconDots class="icon" width="24" height="24" stroke-width="2"/>
                      </a>
                      <div class="dropdown-menu dropdown-menu-end">
                        <a class="dropdown-item" @click="onClickMovePageArtifactHub">
                          Go to the page
                        </a>
                        <a class="dropdown-item" 
                          href="#"
                          data-bs-toggle="modal"
                          data-bs-target="#modal-form"
                          @click="onClickCreate('artifacthub', result)" >
                          Enter content into softwareCatalog
                        </a>
                        <a class="dropdown-item" href="#">
                          Copy file/image to repository
                        </a>
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
  <SoftwareCatalogForm 
    :mode="formMode" 
    :catalog-idx="selectCatalogIdx" 
    :repository-application-info="repositoryApplicationInfo"
    :repository-name="repositoryName"
    @get-list="_getSoftwareCatalogList" />
</template>
<script setup lang="ts">
// Component
import { IconDots } from '@tabler/icons-vue'
import SoftwareCatalogForm from '@/views/softwareCatalog/components/softwareCatalogForm.vue';

// API
import { getSoftwareCatalogList, searchArtifacthubhub, searchDockerhub } from '@/api/softwareCatalog';

// ETC
import { computed, onMounted, ref } from 'vue';
import type { SoftwareCatalog } from '@/views/type/type';
import { useToast } from 'vue-toastification';
// @ts-ignore
import _ from 'lodash';

const toast = useToast()

const catalogList = ref([] as Array<SoftwareCatalog | any>)
const selectCatalogIdx = ref(null as number | null)

const formMode = ref('new')
const repositoryApplicationInfo = ref({} as any)
const repositoryName = ref("" as string)

const searchKeyword = ref("")
const dockerHubSearchList = ref([] as any)
const artifactHubSearchList = ref([] as any)

/**
* @Title Life Cycle
* @Desc SearchKeyword 변수 초기화 / catalogList set Method call
*/
onMounted(async () => {
  searchKeyword.value = ""
  _getSoftwareCatalogList()
})

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
    
    for(let i=0; i<3; i++) {
      dockerHubSearchList.value.push(data.results[i])
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
    for(let i=0; i<3; i++) {
      artifactHubSearchList.value.push(data.packages[i])
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
  formMode.value = 'new'
  selectCatalogIdx.value = 0;
  repositoryApplicationInfo.value = result
  repositoryName.value = repoName
}

/**
* @Method onClickUpdate
* @Desc Update SoftwareCatalog Popup set
*/
const onClickUpdate = (idx: number) => {
  formMode.value = 'update'
  selectCatalogIdx.value = idx;
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
</style>