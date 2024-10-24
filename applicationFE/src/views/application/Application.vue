<template>
  <div class="card w-100">
    <div class="card-header">
      <div class="card-title">
        <h1>Workflow {{ mode === "new" ? "등록" : "수정" }}</h1>
      </div>
    </div>

    <div class="card-body">
      <div class="card-title">
        
        <!-- 워크플로우 명 -->
        <div class="mb-3">
          <label class="form-label required">워크 플로우 명</label>
          <div class="grid gap-0 column-gap-3">
            <input type="text" class="form-control p-2 g-col-11" placeholder="워크플로우 명을 입력하세요" />
            <button v-if="!duplicatedWorkflow" class="btn btn-primary" @click="onClickDuplicatWorkflowName">중복 체크</button>
            <button v-else class="btn btn-success">중복 체크</button>
          </div>
        </div>

        <!-- 목적 -->
        <div class="mb-3">
          <label class="form-label required">목적</label>
          <div class="grid gap-0 column-gap-3">
            <select class="form-select p-2 g-col-12">
              <option v-for="(purpose, idx) in workflowPurposeList" :value="purpose.value" :key="idx">
                {{ purpose.name }}
              </option>
            </select>
          </div>
        </div>

        <!-- 젠킨스 주소 -->
        <div class="mb-3">
          <label class="form-label required">젠킨스 주소</label>
          <div class="grid gap-0 column-gap-3">
            <input type="text" class="form-control p-2 g-col-12" placeholder="젠킨스 주소" :value="jenkinsUrl" disabled/>
          </div>
        </div>

        <!-- 파이프 라인 -->
        <div class="mb-3">
          <div class="grid gap-0 column-gap-3 border-bottom pb-5 pt-5">
            <label class="form-label required p-2 g-col-11">파이프 라인</label>
            <button class="btn btn-primary">스크립트 생성</button>
          </div>
          <div class="grid gap-0">

            <!-- 스크립트 구역 -->
            <div class="p-2 g-col-9">
              <div>스크립트 생성 버튼을 클릭해주세요</div>
            </div>
            <!-- <div v-if="props.workflow.pipelines">
              <draggable :list="props.workflow.pipelines" :group="{
                  name: 'pipelineEidtor',
                  pull: false,
                  put: true
                }" :move="onCheckDraggableEditor" @start="onStartDrag" @end="onFinishDrag">

                <div v-for="(item, idx) in props.workflow.pipelines" :key="idx">
                  <div class="row" :class="{ 'draggable': !item.isDefaultScript }">
                    <span slot="label" class="field-label col-10">
                      {{ item.pipelineCd ? item.pipelineCd : "&nbsp" }}
                    </span>
                    <span class="col-2">
                      <el-button v-if="!item.isDefaultScript" class="btn btn-danger btn-sm"
                        @click="onDeletePipeline(idx)">delete</el-button>
                    </span>
                    <v-ace-editor v-model:value="(props.workflow.pipelines[idx]).pipelineScript" :id="item.pipelineCd"
                      :options="{
                        readOnly: dragFlag,
                        maxLines: 9999,
                        minLines: 10,
                        selectionStyle: 'text',
                        highlightActiveLine: false,
                        cursorStyle: 'smooth',
                        hasCssTransforms: true
                      }" />
                  </div>
                </div>
              </draggable>
            </div> -->



            <!-- 구분 구역 -->
            <div class="p-2 g-col-3">
              <div>test</div>
              <div>test</div>
              <div>test</div>
              <div>test</div>

              
              <!-- <div class="col-3 mt-1">
                <el-collapse v-model="activeName" accordion>
                  <el-collapse-item v-for="(pipeList, pipelineCd) in pipelineScriptList" :name="pipelineCd"
                    :key="pipelineCd">
                    <template #title>
                      <span class="paletteTitle">
                        {{ pipelineCd }}
                      </span>
                    </template>
                    <div>
                      <component :is="components.draggable" :list="pipeList"
                        :group="{ name: 'pipelineEidtor', pull: 'clone', put: false }" :move="onCheckDraggablePalette"
                        :clone="onClonePipeline" @start="onStartDrag" @end="onFinishDrag">
                        <div v-for="(item, index) in pipeList" :key="index" class="paletteItem"
                          @click="onClickPaletteItem(item)">
                          {{ item ? item.pipelineName : '등록된 스테이지가 없습니다.' }}
                        </div>
                      </component>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </div> -->
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getOssList } from '@/api/application'

const router = useRouter();
const route = useRoute();

onMounted(() => {
  setMode()
  setWorkflowPurposeList()
  setJenkinsInfo()
})

// ================================================================================= 모드 세팅
const mode = ref('new' as string)
const setMode = () => {
  if (route.params.workflowId !== undefined)
    mode.value = 'edit'
}

// ================================================================================= 중복체크
const duplicatedWorkflow = ref(false as boolean)
const onClickDuplicatWorkflowName = () => {
  // TODO : 수정필요
  duplicatedWorkflow.value = true
}

// ================================================================================= 목적 목록
interface WorkflowPurpose {
  name: string
  value: string
}
const workflowPurposeList = ref([] as Array<WorkflowPurpose>)
const setWorkflowPurposeList = () => {
  workflowPurposeList.value = [
    {
      name: "배포용",
      value: "deploy"
    },
    {
      name: "실행용",
      value: "run"
    },
      {
      name: "테스트용",
      value: "test"
    },
      {
      name: "웹훅용",
      value: "webhook"
    },
  ]
}

// ================================================================================= 젠킨스 주소 목록
const jenkinsUrl = ref('')
const setJenkinsInfo = async () => {
  try {
    const { data } = await getOssList()
    jenkinsUrl.value = data ? data[0].ossUrl : '젠킨스 정보가 없습니다.'
    console.log(data.ossUrl)
    console.log(data)
  } catch (error) {
    console.log(error)
  }
}





</script>

<style scoped>
.space-between {
  display: flex;
  justify-content: space-between;
}
</style>