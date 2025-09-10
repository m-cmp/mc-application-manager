<template>
  <div class="modal fade" id="modal-form" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">
            Create New Software catalog
          </h5>
          <button 
            type="button" 
            class="btn-close" 
            data-bs-dismiss="modal" 
            aria-label="Close" />
        </div>
        <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">

          <!-- Title -->
          <div class="mb-3">
            <label class="form-label">Title</label>
            <input type="text" class="form-control" id="sc-title" name="title" placeholder="Application name" v-model="catalogDto.name" disabled/>
          </div>
          
          <!-- Summary -->
          <div class="mb-3">
            <label class="form-label">Summary</label>
            <input type="text" class="form-control" id="sc-summary" name="summary" placeholder="Application summary" v-model="catalogDto.summary" />
          </div>

          <!-- Category -->
          <div class="mb-3">
            <label class="form-label">Category</label>
            <select class="form-select" id="sc-category" v-model="catalogDto.category">
              <option value="SERVER" selected>SERVER</option>
              <option value="WAS">WAS</option>
              <option value="DB">DB</option>
              <option value="UTIL">UTIL</option>
              <option value="OBSERVABILITY">OBSERVABILITY</option>
            </select>
          </div>

          <!-- Description -->
          <div class="mb-3">
            <label class="form-label">Description</label>
            <textarea class="form-control" rows="5" id="sc-desc" v-model="catalogDto.description"></textarea>
          </div>

          <!-- Recommend Server Spec -->

<!-- test -->
          <label class="form-label">Spec</label>

          <div class="mb-5">
            <div class="accordion" id="accordion">
              <div class="accordion-item">
                <h2 class="accordion-header" id="headingRecommendSpec">
                  <button class="accordion-button required" type="button" data-bs-toggle="collapse" data-bs-target="#recommendedSpec" aria-expanded="true" aria-controls="recommendedSpec">
                    Recommended Spec
                  </button>
                </h2>
                <div id="recommendedSpec" class="accordion-collapse collapse " show aria-labelledby="headingRecommendSpec" data-bs-parent="#accordion">
                  <div class="accordion-body">
                    <div class="d-flex justify-content-between">
                      <div>
                        <label class="form-label required">CPU</label>
                        <input type="number" class="form-control w-90-per" placeholder="2" v-model="catalogDto.recommendedCpu" />
                      </div>
                      <div>
                        <label class="form-label required">MEMORY</label>
                        <input type="number" class="form-control w-90-per" placeholder="4" v-model="catalogDto.recommendedMemory" />
                      </div>
                      <div>
                        <label class="form-label required">DISK</label>
                        <input type="number" class="form-control w-90-per" placeholder="20" v-model="catalogDto.recommendedDisk" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="accordion-item">
                <h2 class="accordion-header" id="headingMinimumSpec">
                  <button class="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#minimumspec" aria-expanded="true" aria-controls="minimumspec">
                    Minimum Spec
                  </button>
                </h2>
                <div id="minimumspec" class="accordion-collapse collapse " show aria-labelledby="headingMinimumSpec" data-bs-parent="#accordion">
                  <div class="accordion-body">
                    <div class="d-flex justify-content-between">
                      
                      <div>
                        <label class="form-label required">CPU</label>
                        <input type="number" class="form-control w-90-per" placeholder="2" v-model="catalogDto.minCpu" />
                      </div>
                      <div>
                        <label class="form-label required">MEMORY</label>
                        <input type="number" class="form-control w-90-per" placeholder="4" v-model="catalogDto.minMemory" />
                      </div>
                      <div>
                        <label class="form-label required">DISK</label>
                        <input type="number" class="form-control w-90-per" placeholder="20" v-model="catalogDto.minDisk" />
                      </div>

                    </div>
                  </div>
                </div>
              </div>


              <div class="accordion-item">
                <h2 class="accordion-header" id="headingPort">
                  <button class="accordion-button" type="button" data-bs-toggle="collapse" data-bs-target="#port" aria-expanded="true" aria-controls="port">
                    Port
                  </button>
                </h2>
                <div id="port" class="accordion-collapse collapse " show aria-labelledby="headingPort" data-bs-parent="#accordion">
                  <div class="accordion-body">
                    <div>
                      <label class="form-label required">Port</label>

                      <div class="d-flex justify-content-between mb-3">
                        <input type="number" class="form-control w-80-per" placeholder="8080" v-model="catalogDto.defaultPort" />
                        <div class="btn-list">
                          <button class="btn btn-primary" disabled @click="addPort">
                            <IconPlus class="icon icon-tabler icon-tabler-plus m-0" size="24"/>
                          </button>
                          <button class="btn btn-primary" disabled @click="removePort(0)">
                            <IconMinus class="icon icon-tabler icon-tabler-plus m-0" size="24"/>
                          </button>
                        </div>
                      </div>

                      <!-- <div class="d-flex justify-content-between mb-3" v-for="(defaultPort, idx) in catalogDto.defaultPort" :key="idx">
                        <input type="number" class="form-control w-80-per" placeholder="8080" v-model="defaultPort[idx]" />
                        <div class="btn-list">
                          <button class="btn btn-primary" disabled @click="addPort">
                            <IconPlus class="icon icon-tabler icon-tabler-plus" size="24" style="margin: 0px !important;"/>
                          </button>
                          <button class="btn btn-primary" disabled @click="removePort(idx)">
                            <IconMinus class="icon icon-tabler icon-tabler-plus" size="24" style="margin: 0px !important;"/>
                          </button>
                        </div>
                      </div> -->

                    </div>
                  </div>
                </div>
              </div>


              <div class="accordion-item">
                <h2 class="accordion-header" id="headingHpa">

                  <button class="accordion-button d-inline" type="button" data-bs-toggle="collapse" data-bs-target="#hpa" aria-expanded="true" aria-controls="hpa">
                    HPA (For K8S)
                    <input class="form-check-input ms-1 mt-1" type="checkbox" v-model="checkedHPA" :disabled="mode === 'update'"/>
                  </button>

                </h2>
                <div id="hpa" class="accordion-collapse collapse" aria-labelledby="headingHpa" data-bs-parent="#accordion">
                  <div class="accordion-body">
                      <div class="d-flex justify-content-between ">
                        
                        <div>
                          <label class="form-label required">minReplicas</label>
                          <input type="number" class="form-control w-90-per" placeholder="1" v-model="catalogDto.minReplicas"  :disabled="!checkedHPA"/>
                        </div>
                        <div>
                          <label class="form-label required">maxReplicas</label>
                          <input type="number" class="form-control w-90-per" placeholder="10" v-model="catalogDto.maxReplicas"  :disabled="!checkedHPA"/>
                        </div>
                        <div>
                          <div>
                            <label class="form-check-label">CPU (%)</label>
                          </div>
                          <input type="number" class="form-control w-80-per d-inline" placeholder="60" v-model="catalogDto.cpuThreshold" :disabled="!checkedHPA"/> %
                        </div>
                        <div>
                          <div>
                            <label class="form-check-label" >MEMORY (%)</label>
                          </div>
                          <input type="number" class="form-control w-80-per d-inline" placeholder="80" v-model="catalogDto.memoryThreshold" :disabled="!checkedHPA"/> %
                        </div>

                      </div>
                  </div>
                </div>
              </div>

            </div>
          </div>
<!-- test -->


          <!-- <div class="mb-3">
            <label class="form-label required">Recommended Server Spec</label>
            <div  class="d-flex justify-content-between">
              <div>
                <label class="form-label required">CPU</label>
                <input type="number" class="form-control w-90-per" placeholder="2" v-model="catalogDto.recommendedCpu" />
              </div>
              <div>
                <label class="form-label required">MEMORY</label>
                <input type="number" class="form-control w-90-per" placeholder="4" v-model="catalogDto.recommendedMemory" />
              </div>
              <div>
                <label class="form-label required">DISK</label>
                <input type="number" class="form-control w-90-per" placeholder="20" v-model="catalogDto.recommendedDisk" />
              </div>
            </div>
          </div> -->

          <!-- Minimun Spec -->
          <!-- <div class="mb-3">
            <label class="form-label required">Minimun Spec</label>
            <div class="d-flex justify-content-between">
              <div>
                <label class="form-label required">CPU</label>
                <input type="number" class="form-control w-90-per" placeholder="2" v-model="catalogDto.minCpu" />
              </div>
              <div>
                <label class="form-label required">MEMORY</label>
                <input type="number" class="form-control w-90-per" placeholder="4" v-model="catalogDto.minMemory" />
              </div>
              <div>
                <label class="form-label required">DISK</label>
                <input type="number" class="form-control w-90-per" placeholder="20" v-model="catalogDto.minDisk" />
              </div>
            </div>
          </div> -->

          <!-- HPA -->
          <!-- <div class="mb-3">
            <div class="d-flex justify-content-start">
              <label class="form-label me-2">HPA (For K8S)</label>
              <input class="form-check-input" type="checkbox" v-model="checkedHPA" :disabled="mode === 'update'"/>
            </div>
            <div class="d-flex justify-content-between">
              <div>
                <label class="form-label required">minReplicas</label>
                <input type="number" class="form-control w-90-per" placeholder="1" v-model="catalogDto.minReplicas"  :disabled="!checkedHPA"/>
              </div>
              <div>
                <label class="form-label required">maxReplicas</label>
                <input type="number" class="form-control w-90-per" placeholder="10" v-model="catalogDto.maxReplicas"  :disabled="!checkedHPA"/>
              </div>
              <div>
                <div>
                  <label class="form-check-label">CPU (%)</label>
                </div>
                <input type="number" class="form-control w-80-per d-inline" placeholder="60" v-model="catalogDto.cpuThreshold" :disabled="!checkedHPA"/> %
              </div>
              <div>
                <div>
                  <label class="form-check-label" >MEMORY (%)</label>
                </div>
                <input type="number" class="form-control w-80-per d-inline" placeholder="80" v-model="catalogDto.memoryThreshold" :disabled="!checkedHPA"/> %
              </div>
            </div>
          </div> -->


          <!-- Reference -->
          <div class="row" id="sc-ref" v-for="(ref, idx) in refData" :key="idx">
            <div class="col-lg-6">
    
              <!-- Reference Key -->
              <div class="mb-3">
                <label class="form-label">Reference</label>
                <select class="form-select" id="sc-reference-1" v-model="ref.refType">
                  <option value="URL">URL</option>
                  <option value="MANIFEST">MANIFEST</option>
                  <option value="WORKFLOW">WORKFLOW</option>
                  <option value="IMAGE">IMAGE</option>
                  <option value="HOMEPAGE">HOMEPAGE</option>
                  <option value="TAG">TAG</option>
                  <option value="ETC">ETC</option>
                </select>
              </div>
            </div>

            <!-- Reference Value -->
            <div class="col-lg-6">
              <div class="mb-3">
                <label class="form-label">&nbsp;</label>
                <input type="text" class="form-control" id="sc-ref-value-1" name="refValue" placeholder="Ref value" v-model="ref.refValue" />
              </div>
            </div>

            <!-- Reference Description -->
            <div class="mb-3">
              <div class="input-form">
                <input type="text" class="form-control w-80-per" id="sc-ref-desc-1" name="refDescription"
                  placeholder="Ref Description" v-model="ref.refDesc" />
                <div class="btn-list">
                  <button class="btn btn-primary" @click="addRef">
                    <IconPlus class="icon icon-tabler icon-tabler-plus" size="24" style="margin: 0px !important;"/>
                  </button>
                  <button class="btn btn-primary" @click="removeRef(idx)">
                    <IconMinus class="icon icon-tabler icon-tabler-plus" size="24" style="margin: 0px !important;"/>
                  </button>
                </div>
              </div>
            </div>

          </div>
        </div>

        <!-- footer -->
        <div class="modal-footer">
          <a class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setInit">
            Cancel
          </a>
          <a class="btn btn-primary ms-auto" data-bs-dismiss="modal" @click="_createSoftwareCatalog">
            <IconPlus class="icon icon-tabler icon-tabler-plus" size="24" stroke-width="2"/>
            <span v-if="mode === 'new'">Create New Software catalog</span>
            <span v-else-if="mode === 'update'">Update Software catalog</span>
          </a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { IconPlus, IconMinus } from '@tabler/icons-vue'
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
import { watch, computed } from 'vue';
import { createSoftwareCatalog, getSoftwareCaltalogDetail, updateSoftwareCatalog } from '@/api/softwareCatalog';

const toast = useToast()
interface Props {
  mode: string
  catalogIdx: number | null
  repositoryApplicationInfo: any
  repositoryName: string
}
const props = defineProps<Props>()
const emit = defineEmits(['get-list'])

const catalogIdx = computed(() => props.catalogIdx);
const mode = computed(() => props.mode)
const repositoryApplicationInfo = computed(()=> props.repositoryApplicationInfo)
const repositoryName = computed(()=> props.repositoryName)

const catalogDto = ref({} as any);
const refData = ref([] as any)
// const files = ref([] as any)
const checkedHPA = ref(false as boolean)

watch(()=> catalogIdx.value, async () => {
  await setInit();
},{ deep: true });
watch(()=> repositoryApplicationInfo.value, async () => {
  await setInit();
},{ deep: true });

const setInit = async () => {
  if (mode.value === 'update') {
    await _getSoftwareCatalogDetail()
  } else {
    if (repositoryName.value === 'dockerhub')
      setDockerHubToCatalog(repositoryApplicationInfo.value, repositoryName.value)
    else if (repositoryName.value === 'artifacthub')
      setArtifactHubToCatalog(repositoryApplicationInfo.value, repositoryName.value)
    else
      setInitData()
    refData.value = [];
    refData.value.push(
      {
        "refId": 0,
        "refValue": "",
        "refDesc": "",
        "refType": "",
      }
    )
  }
}

const setDockerHubToCatalog = (applicationInfo: any, repositoryName: string) => {
  catalogDto.value = {
    title: applicationInfo.name,
    description: applicationInfo.short_description,
    category: "",
    summary:  applicationInfo.short_description,
    sourceType: repositoryName,

    // icon
    logoUrlLarge: applicationInfo.logo_url.large,
    logoUrlSmall: applicationInfo.logo_url.small,

    // 최소사양
    minCpu: 0,
    minMemory: 0,
    minDisk: 0,

    // 권장사양
    recommendedCpu: 0,
    recommendedMemory: 0,
    recommendedDisk: 0,

    // 임계치
    cpuThreshold: 0,
    memoryThreshold: 0,

    // 최소 / 최대 Replica
    minReplicas: 0,
    maxReplicas: 0,

    catalogRefData: [],
    hpaEnabled: false,

    // port
    // defaultPort: [0] as Array<number>,
    defaultPort: 0 as number,
    
    // package 정보
    packageInfo: {
      packageType: 'DOCKER',
      packageName: applicationInfo.id,
      packageVersion: "latest",
      repositoryUrl: "https://hub.docker.com/_/"+applicationInfo.name,
      dockerImageId: "",
      dockerPublisher: applicationInfo.publisher.name,
      dockerCreatedAt: formatDate(applicationInfo.created_at),
      dockerUpdatedAt: formatDate(applicationInfo.updated_at),
      dockerShortDescription: applicationInfo.short_description,
      dockerSource: applicationInfo.source
    }
  }
}

const setArtifactHubToCatalog = (applicationInfo: any, repositoryName: string) => {
  catalogDto.value = {
    title: applicationInfo.name,
    description: applicationInfo.description,
    category: "",
    summary: "",
    sourceType: repositoryName,

    // icon
    logoUrlLarge: "",
    logoUrlSmall: "",

    // 최소사양
    minCpu: 0,
    minMemory: 0,
    minDisk: 0,

    // 권장사양
    recommendedCpu: 0,
    recommendedMemory: 0,
    recommendedDisk: 0,

    // 임계치
    cpuThreshold: 0,
    memoryThreshold: 0,

    // 최소 / 최대 Replica
    minReplicas: 0,
    maxReplicas: 0,

    catalogRefData: [],
    hpaEnabled: false,

    // defaultPort: [0],
    defaultPort: 0,

    // package 정보
    helmChart: {
      id: 0,
      catalogId: 0,
      chartName: "string",
      chartVersion: "string",
      chartRepositoryUrl: "string",
      valuesFile: "string",
      packageId: "string",
      normalizedName: "string",
      hasValuesSchema: true,
      repositoryName: "string",
      repositoryOfficial: true,
      repositoryDisplayName: "string"
    }
  }
}

const setInitData = () => {
  catalogDto.value = {
    title: "",
    description: "",
    category: "",
    summary: "",
    sourceType: "",

    // icon
    logoUrlLarge: "",
    logoUrlSmall: "",

    // 최소사양
    minCpu: 0,
    minMemory: 0,
    minDisk: 0,

    // 권장사양
    recommendedCpu: 0,
    recommendedMemory: 0,
    recommendedDisk: 0,

    // 임계치
    cpuThreshold: 0,
    memoryThreshold: 0,

    // 최소 / 최대 Replica
    minReplicas: 0,
    maxReplicas: 0,

    // defaultPort: [0],
    defaultPort: 0,

    catalogRefData: [],
    hpaEnabled: false,
  }
}

const _getSoftwareCatalogDetail = async () => {
  try {
    await getSoftwareCaltalogDetail(Number(catalogIdx.value)).then(({ data }) => {
      catalogDto.value = data
      if(catalogDto.value.catalogRefs.length === 0) {
        catalogDto.value.catalogRefs = 
        [
          {
            "refId": 0,
            "refValue": "",
            "refDesc": "",
            "refType": "",
          }
        ]
      }
      
      if(catalogDto.value.hpaEnabled) {
        checkedHPA.value = true
      }

      data.catalogRefs.forEach((catalogRef: any) => {
        if (catalogRef.refType !== null)
          catalogRef.refType = catalogRef.refType.toUpperCase() 
      })
      refData.value = data.catalogRefs;
    })
  } catch(error) {
      console.log(error)
      toast.error('Unable to fetch data.')
  }
}

const addPort = () => {
  catalogDto.value.defaultPort.push('')
}
const removePort = (idx:number) => {
  if(catalogDto.value.defaultPort.length !== 1) {
    catalogDto.value.defaultPort.splice(idx, 1)
  }
}

const addRef = () => {
  refData.value.push({
    "refId": 0,
    "refValue": "",
    "refDesc": "",
    "refType": "URL"
  })
}
const removeRef = (idx:number) => {
  if(refData.value.length !== 1) {
    refData.value.splice(idx, 1)
  }
}

const _createSoftwareCatalog = async () => {

  if (checkedHPA.value) {
    catalogDto.value.hpaEnabled = true
  }

  catalogDto.value.catalogRefData = refData.value;

  if(mode.value == 'new') {     
    await createSoftwareCatalog(catalogDto.value).then(({ data })=> {
      if(data) {
        if(data === null) {
          toast.error('Registration Failed')
          setInit();
        } else {
          toast.success('Registration Success')
          emit('get-list')
        }
      } else {
        toast.error('Regist Failed')
        setInit();
      }
    })
  } else {
    await updateSoftwareCatalog(catalogDto.value).then(({ data })=> {
      if(data) {
        if(data === null) {
          toast.error('Update Failed')
          setInit();
        } else {
          toast.success('Update Success')
          emit('get-list')
        }
      } else {
        toast.error('Update Failed')
        setInit();
      }
    })
  }
}

const formatDate = (isoString:string) => {
  const date = new Date(isoString);

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0'); // 월은 0부터 시작하므로 +1
  const day = String(date.getDate()).padStart(2, '0');

  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');

  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}
</script>
<style scoped>
.input-form {
  width: 100% !important;
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
}
.w-50-per {
  width: 50% !important;
}
.w-80-per {
  width: 80% !important;
}
.w-90-per {
  width: 90% !important;
}
</style>