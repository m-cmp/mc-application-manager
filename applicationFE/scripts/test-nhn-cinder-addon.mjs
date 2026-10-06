import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { ref, computed } from 'vue'

const moduleSource = await readFile(new URL('../src/utils/nhnCinderAddon.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(moduleSource, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const { waitForNhnCinderAddon } = await import('data:text/javascript;base64,' + Buffer.from(js).toString('base64'))
const target = { namespace: 'project-a', clusterName: 'cluster-a' }
const state = (status = 'QUEUED', extra = {}) => ({ id: 'job-1', ...target, state: status, code: '', message: status, ...extra })
const noSleep = async () => {}
let cases = 0
async function test(name, fn) { await fn(); cases++; console.log('PASS ' + name) }

await test('queued/running/ready polls only the original job', async () => {
  const ids = [], remaining = [state('RUNNING'), state('READY')]
  await waitForNhnCinderAddon(state(), target, async id => { ids.push(id); return remaining.shift() }, () => {}, () => true, noSleep)
  assert.deepEqual(ids, ['job-1', 'job-1'])
})
await test('already-ready job requires no poll', async () => {
  await waitForNhnCinderAddon(state('READY'), target, () => assert.fail('Unexpected poll'), () => {}, () => true, noSleep)
})
for (const patch of [{ id: 'other' }, { namespace: 'other' }, { clusterName: 'other' }]) {
  await test('mismatched response stops polling ' + JSON.stringify(patch), async () => {
    await assert.rejects(waitForNhnCinderAddon(state(), target, async () => state('READY', patch), () => {}, () => true, noSleep), /different target/)
  })
}
await test('failed job and unknown state do not become ready', async () => {
  for (const status of ['FAILED', 'UNKNOWN'])
    await assert.rejects(waitForNhnCinderAddon(state(status), target, () => assert.fail('Unexpected poll'), () => {}, () => true, noSleep))
})
await test('Project or modal changes during polling stop status updates', async () => {
  let current = true
  await assert.rejects(waitForNhnCinderAddon(state(), target, () => assert.fail('Unexpected poll'), () => {}, () => current,
    async () => { current = false }), /Project changed/)
})
await test('timeout never restarts an installation', async () => {
  let polls = 0
  await assert.rejects(waitForNhnCinderAddon(state(), target, async () => { polls++; return state() }, () => {}, () => true, noSleep, 2), /longer than expected/)
  assert.equal(polls, 2)
})

// Exercise the actual Vue handlers, including existing StorageClass handling and stale responses.
const source = await readFile(new URL('../src/views/softwareCatalog/components/applicationInstallationForm.vue', import.meta.url), 'utf8')
const start = source.indexOf('const fetchStorageClasses =')
const end = source.indexOf('const isRecommendedStorageClass =', start)
const code = ts.transpileModule(`let storageRequestSequence=0, preparationEpoch=0, installationFormMounted=true;
${source.slice(start, end)}
${source.slice(source.indexOf('const storageClassErrorMessage ='), source.indexOf('const objectStorageEndpointPlaceholder ='))}
return { fetchStorageClasses, installCinderAddon, createNotebookStorageClass, storageClassErrorMessage, invalidate: () => { storageRequestSequence++ }, close: () => { preparationEpoch++ } }`,
  { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
function harness(options = {}) {
  let ready = Boolean(options.ready), classes = options.classes || []
  const calls = []
  const values = Object.fromEntries(Object.entries({
    selectInfra: 'K8S', supportsStorageClassConfig: true, selectNsId: target.namespace, selectCluster: target.clusterName,
    storageClassList: [], selectedStorageClass: '', storageClassLoadError: false, storageClassFailure: '', storageCapability: null,
    storageSetupError: '', storageClassLoading: false, nhnAddonCapability: null, nhnAddonProgress: '', nhnAddonChecking: false,
    nhnAddonInstalling: false, storageCreating: false, selectedClusterProvider: options.nhn === false ? 'aws' : 'nhn',
    isNhnCluster: options.nhn !== false, notebookStorageGi: 10, projectContextKey: 'workspace/project-a',
    newStorageClassName: 'am-local-notebooks', newStorageDiskType: 'General HDD',
    storageClassRequired: true, isJupyterObjectStorageCatalog: false, isBuiltInPersistentCatalog: false, selectedStorageMinimum: 1
  }).map(([key, value]) => [key, ref(value)]))
  const args = {
    ...values,
    computed, _: { isEmpty: value => !value?.length },
    storageErrorDetail: error => error.message,
    isRecommendedStorageClass: () => false,
    getInitialStorageClass: items => items[0]?.name || '',
    getK8sStorageClasses: async params => { calls.push(['classes', params]); return { data: [...classes] } },
    getNhnStorageCapability: async params => { calls.push(['capability', params]); return { data: { supported: true, driverReady: ready, canCreate: ready } } },
    getNhnCinderAddon: async params => { calls.push(['addon', params]); return { data: { canInstall: !options.disabled, state: 'AVAILABLE' } } },
    startNhnCinderAddon: async params => { calls.push(['start', params]); return options.start ? await options.start() : { data: state() } },
    getNhnCinderAddonJob: async (params, id) => {
      calls.push(['poll', params, id])
      if (options.poll) return options.poll()
      ready = true; return { data: state('READY') }
    },
    waitForNhnCinderAddon: (...args) => waitForNhnCinderAddon(...args, noSleep, 3),
    createNhnStorageClass: async (params, body) => { calls.push(['create', params, body]); classes = [{ name: body.name }]; return { data: classes[0] } }
  }
  return { ...values, calls, ...new Function(...Object.keys(args), code)(...Object.values(args)) }
}
await test('actual form installs -> polls READY -> refreshes -> creates and selects StorageClass', async () => {
  const h = harness(); await h.fetchStorageClasses(); await h.installCinderAddon()
  assert.equal(h.storageCapability.value.driverReady, true)
  await h.createNotebookStorageClass()
  assert.equal(h.selectedStorageClass.value, 'am-local-notebooks')
  const actions = h.calls.filter(c => ['start', 'poll', 'create'].includes(c[0]))
  assert.deepEqual(actions.map(c => c[0]), ['start', 'poll', 'create'])
  for (const action of actions) assert.deepEqual(action[1], target)
  assert.equal(h.nhnAddonInstalling.value, false)
})
await test('existing StorageClass still discovers missing Cinder and preserves selection after installation', async () => {
  const h = harness({ classes: [{ name: 'existing' }] }); await h.fetchStorageClasses()
  assert.equal(h.nhnAddonCapability.value.canInstall, true)
  assert.match(h.storageClassErrorMessage.value, /Prepare Cinder/)
  await h.installCinderAddon(); assert.equal(h.selectedStorageClass.value, 'existing')
  assert.equal(h.storageClassErrorMessage.value, '')
})
await test('ready CSI and non-NHN clusters require no NKS API calls', async () => {
  for (const options of [{ ready: true }, { nhn: false }]) {
    const h = harness(options); await h.fetchStorageClasses(); await h.installCinderAddon()
    assert.equal(h.calls.some(c => ['addon', 'start', 'poll'].includes(c[0])), false)
  }
})
await test('disabled automation does not submit a job', async () => {
  const h = harness({ disabled: true }); await h.fetchStorageClasses(); await h.installCinderAddon()
  assert.equal(h.calls.some(c => c[0] === 'start'), false)
})
await test('failed job leaves storage setup blocked and does not create a class', async () => {
  const h = harness({ poll: async () => ({ data: state('FAILED', { message: 'NKS denied operation' }) }) })
  await h.fetchStorageClasses(); await h.installCinderAddon()
  assert.equal(h.storageSetupError.value, 'NKS denied operation'); assert.equal(h.storageCapability.value.driverReady, false)
  assert.equal(h.calls.filter(c => c[0] === 'start').length, 1)
  assert.equal(h.calls.some(c => c[0] === 'create'), false)
})
await test('target changes while request is pending cannot update a new cluster', async () => {
  let resolve
  const h = harness({ start: () => new Promise(r => { resolve = r }) })
  await h.fetchStorageClasses(); const pending = h.installCinderAddon()
  h.selectCluster.value = 'cluster-b'; h.invalidate(); h.nhnAddonProgress.value = 'new target'
  resolve({ data: state() }); await pending
  assert.equal(h.calls.some(c => c[0] === 'poll'), false); assert.equal(h.nhnAddonProgress.value, 'new target')
})
await test('double click shares the pending submission', async () => {
  let resolve
  const h = harness({ start: () => new Promise(r => { resolve = r }) })
  await h.fetchStorageClasses(); const pending = h.installCinderAddon(); await h.installCinderAddon()
  assert.equal(h.calls.filter(c => c[0] === 'start').length, 1)
  resolve({ data: state('READY') }); await pending
})
console.log(`PASS ${cases} NHN Cinder frontend cases`)
