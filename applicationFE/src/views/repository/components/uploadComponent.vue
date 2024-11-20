<template>
  <div class="modal" id="uploadComponent" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">

        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        <div class="modal-body text-left py-4">
          <!-- OSS Title -->
          <h3 class="mb-5">
            File Upload
          </h3>

          <div>
            <!-- Repository 명 -->
            <div class="mb-3">
              <label class="form-label">Name</label>
              <input type="text" class="form-control p-2 g-col-11" :value="props.repositoryName" readonly />
            </div>

            <div class="mb-3">
              <label class="form-label">Path</label>
              <input type="text" class="form-control p-2 g-col-11" v-model="directory" :disabled="props.format != 'raw'"/>
            </div>

            <div class="mb-3">
              <label class="form-label required">File</label>
              <div class="grid gap-0 column-gap-3">
                <input type="file" class="form-control p-2 g-col-11" @change="handleFileChange"/>
                <!-- <button class="btn btn-primary">File</button> -->
              </div>
            </div>

          </div>

        </div>

        <div class="modal-footer">
          <a href="#" class="btn btn-link link-secondary" data-bs-dismiss="modal">
            Cancel
          </a>
          <a href="#" class="btn btn-primary ms-auto" data-bs-dismiss="modal"  @click="fileUpload()">
            Upload
          </a>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToast } from 'vue-toastification';
import { uploadComponent } from '@/api/repository';
import { ref } from 'vue';
// @ts-ignore
import _ from 'lodash';
import { onMounted } from 'vue';

const toast = useToast()
/**
 * @Title Props / Emit
 */
interface Props {
  repositoryName: string
  format: string
}
const props = defineProps<Props>()
const emit = defineEmits(['get-detail'])


onMounted(() => {
  setInit();
})

const directory = ref("/" as string)
const files = ref([] as any)

const handleFileChange = (event: any) => {
  files.value = event.target.files[0];
}

const fileUpload = async () => {

  if(props.format == "raw" && _.isEmpty(directory.value)) {
    toast.error('Path를 입력해주세요.');
    return;
  }

  if(files.value.length == 0) {
    toast.error('등록 요청한 파일이 없습니다.');
    return;
  }
  const formData = new FormData();
  formData.append('assets', files.value);
  formData.append('directory', directory.value);

  const { data } = await uploadComponent("nexus", props.repositoryName, formData);
  if (data)
    toast.success('등록되었습니다.')
  else
    toast.error('등록 할 수 없습니다.')
  emit('get-detail')
}

const setInit = () => {
  directory.value = "/"
  files.value = [];
}
</script>