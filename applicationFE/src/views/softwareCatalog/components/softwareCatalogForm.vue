<template>
  <div class="modal" id="modal-form" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
        <div class="modal-content">
            <div class="modal-header">
                <h5 class="modal-title">Create New Software catalog</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body">
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
                        <option value="server" selected>SERVER</option>
                        <option value="was">WAS</option>
                        <option value="db">DB</option>
                        <option value="util">UTIL</option>
                        <option value="observability">OBSERVABILITY</option>
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
                                <option value="url" selected>URL</option>
                                <option value="manifest">manifest</option>
                                <option value="workflow">workflow</option>
                                <option value="image">image</option>
                                <option value="etc">etc</option>
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
import { createCatalog} from '@/api/softwareCatalog';
import { onMounted } from 'vue';
import { computed } from 'vue';
import { watch } from 'vue';

const toast = useToast()
const catalogDto = ref({} as any);
const refData = ref([] as any)
const files = ref([] as any)

const emit = defineEmits(['get-list'])

onMounted(async () => {
    await setInit()
})

const setInit = async () => {
  console.log("setInit")
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
          "referncetIdx": null,
          "referenceValue": "",
          "referenceDescription": "",
          "referenceType": "url"
      }
  )
}

const addRef = () => {
    console.log("addRef");
    refData.value.push({
        "catalogRefIdx": null,
        "catalogIdx": null,
        "referncetIdx": null,
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
  formData.append('catalogDto', catalogDto.value);

  const { data } = await createCatalog(formData);
  if (data)
    toast.success('등록되었습니다.')
  else
    toast.error('등록 할 수 없습니다.')
    emit('get-list')
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