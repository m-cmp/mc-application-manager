<template>
  <div class="modal" id="ossForm" tabindex="-1">
    <div class="modal-dialog modal-lg" role="document">
      <div class="modal-content">

        <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        <div class="modal-body text-left py-4">
          <!-- OSS Title -->
          <h3 class="mb-5">
            OSS {{ props.mode === 'new' ? '생성' : '수정'}}
          </h3>

          <div>
          <!-- OSS 타입 -->
            <div class="mb-3">
              <!-- <div v-if="ossFormData.ossTypeIdx === 1">
                <input class="d-lb mr-5" type="checkbox" v-model="createJenkinsJobYn">
                <label class="form-label d-lb">등록된 워크플로우 Job 생성 여부</label>
              </div> -->


              <label class="form-label required">OSS 타입</label>
              <div class="grid gap-0 column-gap-3">
                <select v-model="ossFormData.ossTypeIdx" class="form-select p-2 g-col-12">
                  <option :value="0">OSS 타입을 선택하세요.</option>
                  <option v-for="(type, idx) in ossTypeList" :value="type.ossTypeIdx" :key="idx">
                    {{ type.ossTypeName }}
                  </option>
                </select>
              </div>
            </div>

            <!-- OSS 명 -->
            <div class="row mb-3">
              <label class="form-label required">OSS 명</label>
              <div class="grid gap-0 column-gap-3">
                <input type="text" class="form-control p-2 g-col-11" placeholder="OSS 명을 입력하세요" v-model="ossFormData.ossName" @change="initDuplicatedCheckBtn" />
              </div>
            </div>
            
            <!-- OSS 설명 -->
            <div class="mb-3">
              <label class="form-label required">OSS 설명</label>
              <input type="text" class="form-control p-2 g-col-11" placeholder="OSS 설명을 입력하세요" v-model="ossFormData.ossDesc" />
            </div>

            <!-- URL -->
            <div class="mb-3">
              <label class="form-label required">URL</label>
              <input type="text" class="form-control p-2 g-col-11" placeholder="서버 URL을 입력하세요" v-model="ossFormData.ossUrl" @focus="initConnectionCheckBtn" />
            </div>
            
            <div class="row">
              
              <!-- OSS ID -->
              <div class="col">
                <label class="form-label required">OSS ID</label>
                <input type="text" class="form-control p-2 g-col-11" placeholder="OSS 아이디를 입력하세요" v-model="ossFormData.ossUsername" @focus="initConnectionCheckBtn"/>
              </div>

              <!-- OSS PW -->
              <div class="col">
                <label class="form-label required">OSS PW</label>
                <input type="password" class="form-control p-2 g-col-11" placeholder="OSS 비밀번호를 입력하세요" v-model="ossFormData.ossPassword" @click="removePassword" @focus="initConnectionCheckBtn"/>
              </div>

              <div class="col mt-4">
                <button v-if="!duplicatedOss" class="btn btn-primary chk" @click="onClickDuplicatOssName" style="margin: 3px;">중복 체크</button>
                <button v-else class="btn btn-success" style="margin: 3px;">중복 체크</button>
                <button v-if="!connectionCheckedOss" class="btn btn-primary" @click="onClickConnectionCheckOss">연결 확인</button>
                <button v-else class="btn btn-success">연결 확인</button>
              </div>
            </div>
          </div>
        </div>

      <div class="modal-footer">
        <a href="#" class="btn btn-link link-secondary" data-bs-dismiss="modal" @click="setInit()">
          Cancel
        </a>
        <a href="#" class="btn btn-primary ms-auto" data-bs-dismiss="modal"  @click="onClickSubmit()">
          {{props.mode === 'new' ? '생성' : '수정'}}
        </a>
      </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Oss, OssType } from '../../type/type';
import { ref } from 'vue';
import { useToast } from 'vue-toastification';
import { getOssTypeList, getOssTypeFilteredList, duplicateCheck, getOssDetailInfo, registOss, updateOss, ossConnectionChecked } from '@/api/oss';
import { onMounted } from 'vue';
import { computed } from 'vue';
import { watch } from 'vue';

const toast = useToast()
/**
 * @Title Props / Emit
 */
interface Props {
  mode: String,
  ossIdx: number
}
const props = defineProps<Props>()
const emit = defineEmits(['get-oss-list'])

/**
 * @Title Life Cycle
 * @Desc ossIdx 값의 변화에 따라 데이터 set함수 호출  
 */
const ossIdx = computed(() => props.ossIdx);
watch(ossIdx, async () => {
  await setInit();
});
watch(() => props.mode, async () => {
  await _getOssTypeList(props.mode)
})

onMounted(async () => {
  await setInit();
})

// /**
//  * @Title createJenkinsJobYn 
//  * @Desc Jenkins Job 생성 여부
//  */
// const createJenkinsJobYn = ref(false as Boolean)


/**
 * @Title formData 
 * @Desc oss 생성 / 수정데이터
 */
const ossFormData = ref({} as Oss)

/**
 * @Title 초기화 Method
 * @Desc 
 *    1. 생성 모드일경우 / ossIdx 가 달라질경우 데이터 초기화
 *    2. 중복검사 / 연결 확인 버튼 활성화 여부 set
 *    3. 닫기 / 생성 / 수정 버튼 클릭시 데이터 초기화
 */
const setInit = async () => {
  if (props.mode === 'new') {
    ossFormData.value.ossTypeIdx = 0
    ossFormData.value.ossName = ''
    ossFormData.value.ossDesc = ''
    ossFormData.value.ossUrl = ''
    ossFormData.value.ossUsername = ''
    ossFormData.value.ossPassword = ''

    duplicatedOss.value = false
    connectionCheckedOss.value = false
  }
  else {
    const { data } = await getOssDetailInfo(props.ossIdx)
    ossFormData.value = data
    ossFormData.value.ossPassword = decriptPassword(ossFormData.value.ossPassword)

    duplicatedOss.value = true
    connectionCheckedOss.value = true
  }
}

/**
 * @Title ossTypeList / _getOssTypeList
 * @Desc 
 *    ossTypeList : ossType 목록 저장
 *    _getOssTypeList : ossType 목록 API 호출
 */
const ossTypeList = ref([] as Array<OssType>)
const _getOssTypeList = async (mode:String) => {
  try {
    if (mode === 'new') {
      const { data } = await getOssTypeFilteredList()
      ossTypeList.value = data
    }
    else {
      const { data } = await getOssTypeList()
      ossTypeList.value = data
    }
  } catch (error) {
    console.log(error)
  }
}

/**
 * @Title removePassword
 * @Desc 패스워드 클릭시 패스워드 항목 초기화
 */
const removePassword = () => {
  ossFormData.value.ossPassword = ''
  connectionCheckedOss.value = false
}

/**
 * @Title duplicatedOss / onClickDuplicatOssName
 * @Desc 
 *    duplicatedOss : 중복검사 여부
 *    onClickDuplicatOssName : oss명 / ossUrl / ossId 로 중복검사 API 호출
 */
const duplicatedOss = ref(false as boolean)
const onClickDuplicatOssName = async () => {
  const param = {
    ossName: ossFormData.value.ossName,
    ossUrl: ossFormData.value.ossUrl,
    ossUsername: ossFormData.value.ossUsername
  }
  const { data } = await duplicateCheck(param)
  if (!data) {
    toast.success('사용 가능한 이름입니다.')
    duplicatedOss.value = true
  }
  else
    toast.error('이미 사용중인 이름입니다.')
}

/**
 * @Title connectionCheckedOss / onClickConnectionCheckOss
 * @Desc 
 *    connectionCheckedOss : 연결확인 여부
 *    onClickConnectionCheckOss : ossUrl / ossId / ossPassword / ossType 으로 연결확인 API 호출
 */
const connectionCheckedOss = ref(false as boolean)
const onClickConnectionCheckOss = async () => {
  const param = {
    ossUrl: ossFormData.value.ossUrl,
    ossUsername: ossFormData.value.ossUsername,
    ossPassword: encriptPassword(ossFormData.value.ossPassword),
    ossTypeIdx: ossFormData.value.ossTypeIdx
  }
  const { data } = await ossConnectionChecked(param)
  if (data) {
    toast.success('사용 가능한 OSS입니다.')
    connectionCheckedOss.value = true
  }
  else
    toast.error('사용 불가능한 OSS입니다.')
}

/**
 * @Title initDuplicatedCheckBtn
 * @Desc 
 *    initDuplicatedCheckBtn : 연결확인 변수 false
 */
const initDuplicatedCheckBtn = () => {
  duplicatedOss.value = false
}

/**
 * @Title initConnectionCheckBtn
 * @Desc 
 *    initConnectionCheckBtn : 연결확인 변수 false
 */
const initConnectionCheckBtn = () => {
  connectionCheckedOss.value = false
}


/**
 * @Title onClickSubmit
 * @Desc 
 *     1. 생성 / 수정 버튼 클릭시 동작
 *     2. 부모로 부터 받은 mode값에 따라서 생성/수정 Callback 함수 호출후 부모에게 oss목록 api 호출  
 */
const onClickSubmit = async () => {
  ossFormData.value.ossPassword = encriptPassword(ossFormData.value.ossPassword)
  if (props.mode === 'new') {
    await _registOss().then(() => {
    emit('get-oss-list')
  })
  }
  else
    await _updateOss().then(() => {
    emit('get-oss-list')
  })
  setInit()
}

/**
 * @Title _registOss
 * @Desc 생성 Callback 함수 / 생성 api 호출
 */
const _registOss = async () => {
  const { data } = await registOss(ossFormData.value)
  if (data)
    toast.success('등록되었습니다.')
  else
    toast.error('등록 할 수 없습니다.')
}

/**
 * @Title _updateOss
 * @Desc 수정 Callback 함수 / 수정 api 호출
 */
const _updateOss = async () => {
  const { data } = await updateOss(ossFormData.value)
  if (data)
    toast.success('등록되었습니다.')
  else
    toast.error('등록 할 수 없습니다.')
}

/**
 * @Title encriptPassword
 * @param password
 * @Desc 평문으로 인자값을 받으며 Base64로 인코딩 하는 함수 
 */
const encriptPassword = (password:string) => {
  return btoa(password)
}

/**
 * @Title decriptPassword
 * @param password
 * @Desc Base64로 인자값을 받으며 평문으로 디코딩 하는 함수 
 */
const decriptPassword = (password: string) => {
  return atob(password)
}

</script>

<!-- <style scoped>
.d-lb {
  display: inline-block;
}
.mr-5 {
   margin-right: 5px;
}
</style> -->