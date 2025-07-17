<template>
  <div class="modal fade" id="deleteOss" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">

        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        <div class="modal-status bg-danger"></div>
        <div class="modal-body text-left py-4">
          <!-- OSS Title -->
          <h3 class="mb-5">
            Delete OSS
          </h3>

          <h4>Are you sure you want to delete {{ props.ossName }}?</h4>

        </div>

        <div class="modal-footer">
          <a href="#" class="btn btn-link link-secondary" data-bs-dismiss="modal">
            Cancel
          </a>
          <a href="#" class="btn btn-primary ms-auto" data-bs-dismiss="modal" @click="onClickDelete()">
            Delete
          </a>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToast } from 'vue-toastification';
import { deleteOss } from '@/api/oss';

const toast = useToast()
/**
 * @Title Props / Emit
 */
interface Props {
  ossName: string
  ossIdx: number
}
const props = defineProps<Props>()
const emit = defineEmits(['get-oss-list'])

/**
 * @Title onClickDelete
 * @Desc 삭제 버튼 클릭시 동작 / 삭제 api 호출
 */
const onClickDelete = async () => {
  const { data } = await deleteOss(props.ossIdx)
  if (data)
    toast.success('Deleted successfully.')
  else
    toast.error('Failed to delete.')
  emit('get-oss-list')
}
</script>