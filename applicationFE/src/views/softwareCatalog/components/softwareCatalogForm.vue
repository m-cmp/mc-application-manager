<template>
  <div class="modal" id="modal-form" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">Create New Software catalog</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body" style="max-height: calc(100vh - 200px);overflow-y: auto;">
                <div class="mb-3">
                    <label class="form-label">Title</label>
                    <input type="text" class="form-control" id="sc-title" name="title" placeholder="Application name" v-model="catalogDto.catalogTitle" />
                </div>
                <div class="mb-3">
                    <label class="form-label">Summary</label>
                    <input type="text" class="form-control" id="sc-summary" name="summary" placeholder="Application summary" v-model="catalogDto.catalogSummary" />
                </div>
                <div class="mb-3">
                    <label class="form-label">Icon</label>
                    <input type="file" class="form-control" id="sc-icon" name="icon" placeholder="Icon File" @change="handleFileChange" />
                </div>
                <div class="mb-3">
                    <label class="form-label">Category</label>
                    <select class="form-select" id="sc-category" v-model="catalogDto.catalogCategory">
                        <option value="SERVER" selected>SERVER</option>
                        <option value="WAS">WAS</option>
                        <option value="DB">DB</option>
                        <option value="UTIL">UTIL</option>
                        <option value="OBSERVABILITY">OBSERVABILITY</option>
                    </select>
                </div>
                <div class="mb-3">
                    <label class="form-label">Description</label>
                    <textarea class="form-control" rows="5" id="sc-desc" v-model="catalogDto.catalogDescription"></textarea>
                </div>
                <div class="row" id="sc-ref" v-for="(ref, idx) in refData" :key="idx">
                    <div class="col-lg-6">
                        <div class="mb-3">
                            <label class="form-label">Reference</label>
                            <select class="form-select" id="sc-reference-1" v-model="ref.referenceType">
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
                    <div class="col-lg-6">
                        <div class="mb-3">
                            <label class="form-label">&nbsp;</label>
                            <input type="text" class="form-control" id="sc-ref-value-1" name="refValue" placeholder="Ref value" v-model="ref.referenceValue" />
                        </div>
                    </div>
                    <div class="mb-3">
                        <div class="input-form">
                            <input type="text" class="form-control" style="width:80% !important" id="sc-ref-desc-1" name="refDescription" placeholder="Ref Description" v-model="ref.referenceDescription" />
                            <div class="btn-list">
                                <button class="btn btn-primary" @click="addRef" style="text-align: center !important;">
                                    <svg  xmlns="http://www.w3.org/2000/svg"  width="24"  height="24"  viewBox="0 0 24 24"  fill="none"  stroke="currentColor"  stroke-width="2"  stroke-linecap="round"  stroke-linejoin="round"  class="icon icon-tabler icons-tabler-outline icon-tabler-plus" style="margin: 0 !important;">
                                    <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
                                    <path d="M12 5l0 14" />
                                    <path d="M5 12l14 0" />
                                    </svg>
                                </button>
                                <button class="btn btn-primary" @click="removeRef(idx)">
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
            <div class="modal-footer">
                <a class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setInit">
                    Cancel
                </a>
                <a class="btn btn-primary ms-auto" data-bs-dismiss="modal" @click="createSoftwareCatalog">
                    <svg xmlns="http://www.w3.org/2000/svg" class="icon icon-tabler icon-tabler-plus" width="24" height="24" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" fill="none" stroke-linecap="round" stroke-linejoin="round">
                        <path stroke="none" d="M0 0h24v24H0z" fill="none"></path>
                        <path d="M12 5l0 14"></path>
                        <path d="M5 12l14 0"></path>
                    </svg>
                    Create New Software catalog
                </a>
            </div>
        </div>
    </div>
</div>
</template>

<script setup lang="ts">
import type { Repository } from '../../type/type';
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
import { onMounted, watch, computed } from 'vue';
import axios from 'axios'

/**
 * @Title Props / Emit
 */
 interface Props {
  mode: String
  catalogIdx: Number
}

const toast = useToast()
const catalogDto = ref({} as any);
const refData = ref([] as any)
const files = ref([] as any)

const splitUrl = window.location.host.split(':');
const baseUrl = window.location.protocol + '//' + splitUrl[0] + ':18084'
// const baseUrl = "http://210.217.178.130:18084";

const emit = defineEmits(['get-list'])
const props = defineProps<Props>()
const catalogIdx = computed(() => props.catalogIdx);
watch(catalogIdx, async () => {
  await setInit();
});
onMounted(async () => {
    await setInit()
})

const setInit = async () => {
  if(props.mode == 'update') {
    await _getSoftwareCatalogDetail()
  } else {
      catalogDto.value = {
        "catalogIdx": null,
        "catalogTitle": "",
        "catalogDescription": "",
        "catalogSummary": "",
        "catalogCategory": "",
        "catalogRefData": []
      }
      refData.value = [];
      refData.value.push(
          {
              "catalogRefIdx": null,
              "catalogIdx": null,
              "referncetIdx": 0,
              "referenceValue": "",
              "referenceDescription": "",
              "referenceType": "url"
          }
      )
  }
}

const _getSoftwareCatalogDetail = async () => {
    try {
            const response = await axios.get(baseUrl + '/catalog/software/' + props.catalogIdx);
            console.log("response : ", response)

            catalogDto.value = response.data
            refData.value = response.data.catalogRefData;
        } catch(error) {
            console.log(error)
            toast.error('데이터를 가져올 수 없습니다.')
        }
}

const addRef = () => {
    console.log("addRef");
    refData.value.push({
        "catalogRefIdx": null,
        "catalogIdx": null,
        "referncetIdx": 0,
        "referenceValue": "",
        "referenceDescription": "",
        "referenceType": "url"
    })
    // location.reload()
}
const removeRef = (idx:number) => {
    if(refData.value.length !== 1) {
        refData.value.splice(idx, 1)
    }
}

const handleFileChange = (event: any) => {
  files.value = event.target.files[0];
}

const createSoftwareCatalog = async () => {
    const formData = new FormData();
    formData.append('iconFile', files.value);
    
    catalogDto.value.catalogRefData = refData.value;
    formData.append('catalogDto', new Blob([JSON.stringify(catalogDto.value)], {
        type: 'application/json'
    }));
    
    if(props.mode == 'new') {     
        const response = await axios.post(baseUrl + '/catalog/software', formData, {
          headers: {
            'Content-Type': 'multipart/form-data'
          }
        });
        
        if(response.data) {
            if(response.data.data == null) {
                toast.error('등록 할 수 없습니다.')
                setInit();
            } else {
                toast.success('등록되었습니다.')
                emit('get-list')
            }
        } else {
          toast.error('등록 할 수 없습니다.')
          setInit();
        }

    } else {
        const response = await axios.put(baseUrl + '/catalog/software', formData, {
          headers: {
            'Content-Type': 'multipart/form-data'
          }
        });
      
        if(response.data) {
          toast.success('수정되었습니다.')
          emit('get-list')
        } else {
          toast.error('수정 할 수 없습니다.')
          setInit();
        }
    }

}

</script>
<style scoped>
.input-form {
    width: 100% !important;
    display: flex;
    gap: 10px;
    margin-bottom: 10px;
  }
</style>