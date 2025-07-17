<template>
  <div class="modal fade" id="deleteRepository" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">

        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        <div class="modal-status bg-danger"></div>
        <div class="modal-body text-left py-4">
          <!-- OSS Title -->
          <h3 class="mb-5">
            Delete Repository
          </h3>

          <h4>Are you sure you want to delete {{ props.repositoryName }}?</h4>

        </div>

        <div class="modal-footer">
          <a href="#" class="btn btn-link link-secondary" data-bs-dismiss="modal">
            Cancel
          </a>
          <a href="#" class="btn btn-primary ms-auto" data-bs-dismiss="modal"  @click="onClickDelete()">
            Delete
          </a>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useToast } from 'vue-toastification';
import { deleteRepository } from '@/api/repository';

const toast = useToast()
/**
 * @Title Props / Emit
 */
interface Props {
  repositoryName: string
}
const props = defineProps<Props>()
const emit = defineEmits(['get-repository-list'])

/**
 * @Title onClickDelete
 * @Desc 삭제 버튼 클릭시 동작 / 삭제 api 호출
 */
const onClickDelete = async () => {
  const { data } = await deleteRepository("nexus", props.repositoryName)
  if (data)
    toast.success('Deleted successfully.')
  else
    toast.error('Failed to delete.')
    emit('get-repository-list')
}
</script>