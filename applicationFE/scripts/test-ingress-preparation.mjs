import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { ref, computed } from 'vue'

const moduleSource = await readFile(new URL('../src/utils/ingressPreparation.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(moduleSource, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const { waitForIngressPreparation } = await import('data:text/javascript;base64,' + Buffer.from(js).toString('base64'))
const target = { namespace: 'project-a', clusterName: 'cluster-a' }
const state = (status = 'QUEUED', extra = {}) => ({ id: 'job-1', ...target, state: status, message: status, ...extra })
let cases = 0
async function test(name, fn) { await fn(); cases++; console.log('PASS ' + name) }
const noSleep = async () => {}

await test('queued/running/ready progress polls only the submitted job', async () => {
  const calls = [], messages = [], remaining = [state('RUNNING'), state('READY')]
  await waitForIngressPreparation(state(), target, async id => { calls.push(id); return remaining.shift() }, m => messages.push(m), () => true, noSleep)
  assert.deepEqual(calls, ['job-1', 'job-1']); assert.deepEqual(messages, ['QUEUED', 'RUNNING', 'READY'])
})
await test('already-ready cluster never polls', async () => {
  await waitForIngressPreparation(state('READY'), target, () => assert.fail('Unexpected poll'), () => {}, () => true, noSleep)
})
for (const patch of [{ id: 'other' }, { namespace: 'other' }, { clusterName: 'other' }]) {
  await test(`mismatched response ${JSON.stringify(patch)} stops deployment`, async () => {
    await assert.rejects(waitForIngressPreparation(state(), target, async () => state('READY', patch), () => {}, () => true, noSleep), /different target/)
  })
}
for (const initial of [undefined, {}, state('UNKNOWN'), state('FAILED', { message: 'DNS validation failed' })]) {
  await test(`invalid or failed status ${JSON.stringify(initial)} never permits deployment`, async () => {
    await assert.rejects(waitForIngressPreparation(initial, target, () => assert.fail('Unexpected poll'), () => {}, () => true, noSleep))
  })
}
await test('form cancel or Project change during wait prevents another poll', async () => {
  let current = true
  await assert.rejects(waitForIngressPreparation(state(), target, () => assert.fail('Unexpected poll'), () => {}, () => current,
    async () => { current = false }), /Project changed/)
})
await test('form change during a pending status response rejects even READY', async () => {
  let current = true
  await assert.rejects(waitForIngressPreparation(state(), target, async () => { current = false; return state('READY') }, () => {}, () => current, noSleep), /Project changed/)
})
await test('timeout and network failure never submit a replacement job', async () => {
  let polls = 0
  await assert.rejects(waitForIngressPreparation(state(), target, async () => { polls++; return state() }, () => {}, () => true, noSleep, 2), /taking longer/)
  assert.equal(polls, 2)
  await assert.rejects(waitForIngressPreparation(state(), target, async () => { throw new Error('network failed') }, () => {}, () => true, noSleep), /network failed/)
})

// Execute the actual form's deployment function, not a duplicate implementation of its orchestration.
const source = await readFile(new URL('../src/views/softwareCatalog/components/applicationInstallationForm.vue', import.meta.url), 'utf8')
const start = source.indexOf('const runInstall =')
const end = source.indexOf('const buildIngressPayload =', start)
const formCode = ts.transpileModule(`let installationFormMounted=true, preparationEpoch=0, specCheckVersion=0;
${source.slice(start,end)}
${source.slice(end, source.indexOf('const specCheck =', end))}
return { runInstall, cancel: () => { preparationEpoch++ }, invalidate: () => { specCheckVersion++ }, unmount: () => { installationFormMounted=false } }`,
{ compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
function harness(options = {}) {
  const calls = []
  const values = Object.fromEntries(Object.entries({
    deploying: false, deploymentCompleted: false, modalTitle: 'Application Installation', selectInfra: options.infra || 'K8S', specCheckFlag: false,
    specChecking: false, projectScopeError: '', servicePortCidr: '203.0.113.4/32',
    ingressData: { ingressEnabled: options.ingress !== false, ingressHost: 'app.company.com', ingressPath: '/', ingressTlsEnabled: Boolean(options.tls) },
    isJupyterObjectStorageCatalog: Boolean(options.jupyter), projectContextKey: 'workspace/project-a',
    objectStorageData: { selectedStorageIds: ['storage-a'], jupyterToken: 'unit-test-token-12345' }, objectStorageCheckPassed: true,
    inputServicePort: '', selectNsId: target.namespace, selectCluster: target.clusterName, isIbmCluster: options.ibm !== false,
    k8sOpenIngress: true, selectedCatalogIdx: 7, hpaData: {}, workloadRebalancingEnabled: false, selectedResourceType: 'helm',
    selectDeploymentType:'Standalone',canSelectClustering:false,vmNetworkExposureMode:'PRIVATE',vmTargetMode:'VM',
    inputApplications:'Nginx',selectMci:'infra-a',selectedVmList:['vm-a'],selectVmNodeGroupId:'',isTargetLocked:false
  }).map(([k,v]) => [k,ref(v)]))
  const args = {
    ...values,
    toast: { success: m => calls.push(['success',m]), error: m => calls.push(['error',m]) },
    validateStorageClassSelection: () => true,
    emitDeploymentEvent: (...e) => calls.push(['event',...e]), getDeploymentId: () => 123,
    buildK8sAdditionalConfig: () => ({}),
    buildVmAdditionalConfig: () => ({}), props:{},
    runVmInstall: async payload => { calls.push(['deploy',payload]); return {data:{id:123}} },
    normalizeIngressHost: host => host.trim().toLowerCase(),
    effectiveIngressClass: computed(() => values.isIbmCluster.value ? 'public-iks-k8s-nginx' : 'nginx'),
    startIngressPreparation: async payload => { calls.push(['prepare',payload]); return { data: state(options.initial || 'QUEUED') } },
    getIngressPreparation: async (ns,id) => {
      calls.push(['poll',ns,id]); return { data: options.poll ? await options.poll() : state('READY') }
    },
    waitForIngressPreparation: (...args) => waitForIngressPreparation(...args,noSleep,10),
    runK8SInstall: async payload => {
      calls.push(['deploy',payload]);
      return options.deploy ? options.deploy(payload) : { data: { id: 123 } }
    }
  }
  return { ...values, calls, ...new Function(...Object.keys(args),formCode)(...Object.values(args)) }
}
await test('actual IBM Deploy uses prepare -> poll READY -> application API with identical target', async () => {
  const h=harness(); await h.runInstall()
  assert.deepEqual(h.calls.filter(c => ['prepare','poll','deploy'].includes(c[0])).map(c => c[0]), ['prepare','poll','deploy'])
  const prepared=h.calls.find(c => c[0]==='prepare')[1], deployed=h.calls.find(c => c[0]==='deploy')[1]
  for(const key of ['namespace','clusterName','catalogId','ingressHost','servicePortCidr']) assert.equal(prepared[key],deployed[key])
  assert.equal(deployed.openServicePort,false); assert.equal(h.deploying.value,false)
})
for(const event of ['cancel','invalidate','unmount','project']) {
  await test(`actual form ${event} during preparation does not call application deployment`, async () => {
    let h
    h=harness({ poll: async () => {
      if(event==='project') h.projectContextKey.value='workspace/project-b'; else h[event]()
      return state('READY')
    } })
    await h.runInstall(); assert.equal(h.calls.filter(c => c[0]==='deploy').length,0)
    assert.equal(h.deploying.value,false)
  })
}
await test('failed certificate/LB preparation stops actual application creation', async () => {
  const h=harness({ poll: async () => state('FAILED',{ message:'Certificate failed' }) }); await h.runInstall()
  assert.equal(h.calls.filter(c => c[0]==='deploy').length,0)
  assert.ok(h.calls.some(c => c[0]==='error' && c[1]==='Certificate failed'))
})
await test('IBM Jupyter overrides catalog TLS to HTTP while the non-IBM HTTP-only guard remains', async () => {
  const ibm=harness({jupyter:true,tls:true}); await ibm.runInstall()
  assert.equal(ibm.calls.filter(c => c[0]==='deploy').length,1)
  assert.equal(ibm.calls.find(c => c[0]==='deploy')[1].ingressTlsEnabled,false)
  const other=harness({jupyter:true,tls:true,ibm:false}); await other.runInstall()
  assert.equal(other.calls.filter(c => c[0]==='deploy').length,0)
})
for(const options of [{ibm:false},{ingress:false}]) {
  await test(`existing non-IBM/non-Ingress deployment is unchanged ${JSON.stringify(options)}`, async () => {
    const h=harness(options); await h.runInstall()
    assert.equal(h.calls.filter(c => c[0]==='prepare').length,0)
    assert.equal(h.calls.filter(c => c[0]==='deploy').length,1)
  })
}
await test('duplicate Deploy clicks do not create a second preparation', async () => {
  let finish
  const h=harness({ poll: () => new Promise(resolve => { finish=resolve }) })
  const first=h.runInstall(); await h.runInstall()
  for(let i=0;i<10 && !finish;i++) await Promise.resolve()
  finish(state('READY')); await first
  assert.equal(h.calls.filter(c => c[0]==='prepare').length,1)
  assert.equal(h.calls.filter(c => c[0]==='deploy').length,1)
})
const disabledCode = source.slice(source.indexOf('const deployDisabled = computed('), source.indexOf('function getDefaultObjectStorageData('))
function disabled(h) {
  const args={...h,computed,storageClassRequired:ref(false),storageClassErrorMessage:ref(''),_:{isEmpty:value=>!value}}
  return new Function(...Object.keys(args),disabledCode+'\nreturn deployDisabled.value')(...Object.values(args))
}
for(const options of [{ibm:true,tls:false},{ibm:true,tls:true},{ibm:false},{ibm:true,ingress:false},{infra:'VM',ibm:false}]) {
  await test(`completed deployment remains locked against sequential clicks ${JSON.stringify(options)}`,async()=>{
    const h=harness(options); await h.runInstall()
    assert.equal(h.deploymentCompleted.value,true)
    assert.equal(h.deploying.value,false)
    assert.equal(disabled(h),true)
    await h.runInstall()
    assert.equal(h.calls.filter(c=>c[0]==='deploy').length,1)
    assert.equal(h.calls.filter(c=>c[0]==='event'&&c[1]==='DEPLOY_SUCCEEDED').length,1)
    if(options.ibm!==false&&options.ingress!==false){
      for(const kind of ['prepare','deploy'])assert.equal(h.calls.find(c=>c[0]===kind)[1].ingressTlsEnabled,false)
    }
  })
}
for(const failure of ['preparation','application','empty-response']) {
  await test(`failure can be retried without setting completed: ${failure}`,async()=>{
    let attempts=0
    const h=harness(failure==='preparation'
      ? {poll:async()=>++attempts===1?state('FAILED',{message:'Preparation failed'}):state('READY')}
      : {deploy:async()=>{
        if(++attempts===1){if(failure==='empty-response')return {data:null};throw new Error('Application failed')}
        return {data:{id:123}}
      }})
    await h.runInstall(); assert.equal(h.deploymentCompleted.value,false); assert.equal(disabled(h),false)
    await h.runInstall(); assert.equal(h.deploymentCompleted.value,true); assert.equal(disabled(h),true)
  })
}
for(const event of ['cancel','invalidate','unmount','project']) {
  await test(`late app response does not mark another form as completed after ${event}`,async()=>{
    let finish
    const h=harness({initial:'READY',deploy:()=>new Promise(resolve=>{finish=resolve})})
    const pending=h.runInstall()
    for(let i=0;i<10&&!finish;i++)await Promise.resolve()
    if(event==='project')h.projectContextKey.value='workspace/project-b';else h[event]()
    finish({data:{id:123}});await pending
    assert.equal(h.deploymentCompleted.value,false)
    assert.equal(h.calls.filter(c=>c[0]==='event'&&c[1]==='DEPLOY_SUCCEEDED').length,0)
  })
}
assert.ok(!source.includes('ingressPreparationMessage'))
assert.match(source,/const setInit = async \(\) => \{\s*preparationEpoch\+\+\s*deploymentCompleted\.value = false/)
await test('closing a completed form retains its guard until the next opening',async()=>{
  let resets=0,events=0
  const completed=ref(true)
  const cancelCode=ts.transpileModule(source.slice(source.indexOf('const handleCancel ='),source.indexOf('const viewAppsStatus =')),
    {compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText
  const cancel=new Function('deploymentCompleted','deploying','emit','setInit',
    'let preparationEpoch=0;\n'+cancelCode+'\nreturn handleCancel;')(completed,ref(false),()=>events++,async()=>resets++)
  await cancel();assert.equal(completed.value,true);assert.equal(resets,0);assert.equal(events,1)
  completed.value=false;await cancel();assert.equal(resets,1)
})
for(const mode of ['modal','embedded','already-hidden']){
  await test('Apps Status navigation cleans up only the visible local modal: '+mode,async()=>{
    const calls=[]
    let hidden
    const element={classList:{contains:()=>mode!=='already-hidden'},addEventListener:(name,callback)=>{assert.equal(name,'hidden.bs.modal');hidden=callback}}
    const navigationCode=ts.transpileModule(source.slice(source.indexOf('const viewAppsStatus ='),source.indexOf('const getDeploymentTarget =')),
      {compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText
    const navigate=new Function('document','props','Modal','router',navigationCode+'\nreturn viewAppsStatus;')(
      {getElementById:()=>element},{formId:'test-form',embedded:mode==='embedded'},
      {getInstance:()=>({hide:()=>{calls.push('hide');hidden()}})},
      {push:async route=>{assert.equal(route.name,'applicationStatus');calls.push('navigate')}})
    await navigate();assert.deepEqual(calls,mode==='modal'?['hide','navigate']:['navigate'])
  })
}
assert.match(source,/addEventListener\('hide\.bs\.modal', onInstallationModalHide\)/)
assert.match(source,/removeEventListener\('hide\.bs\.modal', onInstallationModalHide\)/)
console.log(`Ingress preparation tests passed (${cases} cases).`)
