import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { ref, watch } from 'vue'

// Execute the production form functions and its real Vue invalidation watcher (no browser/network).
const source = await readFile(new URL('../src/views/softwareCatalog/components/applicationInstallationForm.vue', import.meta.url), 'utf8')
function between(start, end) {
  const begin = source.indexOf(start)
  const finish = source.indexOf(end, begin)
  assert.ok(begin >= 0 && finish > begin, `Missing production block: ${start}`)
  return source.slice(begin, finish)
}
const code = ts.transpileModule([
  'let specCheckVersion = 0;',
  between('const normalizeIngressHost =', 'const _getSoftwareCatalogList ='),
  between('const onChangeForm =', 'const onSelectVm ='),
  between('const buildIngressPayload =', 'const selectedCatalogInfo ='),
  between('watch([selectInfra,', 'watch(selectInfra, async'),
  'return { specCheck, buildIngressPayload, onChangeForm };'
].join('\n'), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText

function harness(options = {}) {
  const calls = []
  const state = Object.fromEntries(Object.entries({
    selectInfra: 'K8S', selectNsId: 'project-a', selectCluster: 'cluster-a', selectMci: 'mci-a',
    selectedVmList: ['vm-a'], vmTargetMode: 'VM', selectVmNodeGroupId: '', selectedNodeGroupVmIds: ['vm-a', 'vm-b'],
    selectedCatalogIdx: 7, selectedStorageClass: 'standard', projectContextKey: 'ws/project-a',
    modalTitle: 'Application Installation', projectScopeError: '', specCheckFlag: true, specChecking: false,
    specCheckErrors: [], specCheckWarnings: [],
    ingressData: { ingressEnabled: true, ingressHost: 'https://APP.Example.com:30880/ignored',
      ingressPath: '/app', ingressClass: 'nginx', ingressTlsEnabled: false, ingressTlsSecret: '' }
  }).map(([name, value]) => [name, ref(value)]))
  const env = {
    ...state, watch,
    validateStorageClassSelection: () => true,
    toast: { error: message => calls.push(['toast-error', message]), success: message => calls.push(['toast-success', message]) },
    confirm: message => { calls.push(['confirm', message]); return options.confirm ?? true },
    k8sIngressCheck: async payload => {
      calls.push(['ingress', payload])
      return options.ingress ? options.ingress(payload) : { data: { valid: true, errors: [], warnings: [] } }
    },
    k8sSpecCheck: async payload => {
      calls.push(['resources', payload])
      return options.resources ? options.resources(payload) : { data: true }
    },
    vmSpecCheck: async payload => { calls.push(['vm', payload]); return { data: true } }
  }
  const functions = new Function(...Object.keys(env), code)(...Object.values(env))
  return { ...state, ...functions, calls }
}

let cases = 0
async function test(name, fn) { await fn(); cases++; console.log(`PASS ${name}`) }
const count = (h, kind) => h.calls.filter(c => c[0] === kind).length

await test('current normalized inputs are checked before resources; an empty UI Secret is omitted', async () => {
  const h = harness()
  await h.specCheck()
  assert.equal(h.specCheckFlag.value, false)
  assert.equal(h.specChecking.value, false)
  assert.deepEqual(h.calls.slice(0, 2).map(c => c[0]), ['ingress', 'resources'])
  assert.deepEqual(h.calls[0][1], {
    namespace: 'project-a', clusterName: 'cluster-a', catalogId: 7, ingressEnabled: true,
    ingressHost: 'app.example.com', ingressPath: '/app', ingressClass: 'nginx', ingressTlsEnabled: false, ingressTlsSecret: null
  })
  assert.deepEqual(h.calls[0][1], { namespace: 'project-a', clusterName: 'cluster-a', catalogId: 7, ...h.buildIngressPayload() })
})

await test('conflicts cannot enter the low-spec override flow', async () => {
  const h = harness({ ingress: () => ({ data: { valid: false, errors: ['Host/Path conflict'], warnings: [] } }) })
  await h.specCheck()
  assert.equal(h.specCheckFlag.value, true)
  assert.deepEqual(h.specCheckErrors.value, ['Host/Path conflict'])
  assert.equal(count(h, 'resources'), 0)
  assert.equal(count(h, 'confirm'), 0)
})

await test('TLS readiness warnings are visible but do not block deployment', async () => {
  const h = harness({ ingress: () => ({ data: { valid: true, errors: [], warnings: ['Secret is not ready'] } }) })
  h.ingressData.value.ingressTlsEnabled = true
  h.ingressData.value.ingressTlsSecret = 'selected-cert'
  await h.specCheck()
  assert.equal(h.specCheckFlag.value, false)
  assert.deepEqual(h.specCheckWarnings.value, ['Secret is not ready'])
  assert.equal(h.calls[0][1].ingressTlsSecret, 'selected-cert')
})

for (const input of ['ingressHost', 'ingressPath', 'ingressClass', 'ingressEnabled', 'ingressTlsEnabled', 'ingressTlsSecret']) {
  await test(`changing ${input} invalidates the completed check`, async () => {
    const h = harness(); await h.specCheck()
    h.specCheckWarnings.value = ['old warning']
    h.ingressData.value[input] = typeof h.ingressData.value[input] === 'boolean' ? !h.ingressData.value[input] : 'changed'
    assert.equal(h.specCheckFlag.value, true)
    assert.deepEqual(h.specCheckWarnings.value, [])
  })
}
for (const input of ['selectNsId', 'selectCluster', 'selectedCatalogIdx', 'projectContextKey', 'selectedStorageClass']) {
  await test(`changing ${input} invalidates the completed check`, async () => {
    const h = harness(); await h.specCheck()
    h[input].value = input === 'selectedCatalogIdx' ? 8 : 'changed'
    assert.equal(h.specCheckFlag.value, true)
  })
}
for (const stage of ['ingress', 'resources']) {
  await test(`late ${stage} response cannot validate changed or reverted form inputs`, async () => {
    let finish
    const pending = new Promise(resolve => { finish = resolve })
    const h = harness({ [stage]: () => pending })
    const run = h.specCheck()
    await Promise.resolve(); await Promise.resolve()
    const previous = h.ingressData.value.ingressHost
    h.ingressData.value.ingressHost = 'changed.example.com'
    h.ingressData.value.ingressHost = previous
    finish({ data: stage === 'ingress' ? { valid: true, errors: [], warnings: ['stale'] } : true })
    await run
    assert.equal(h.specCheckFlag.value, true)
    assert.equal(h.specChecking.value, false)
    assert.deepEqual(h.specCheckWarnings.value, [])
    assert.equal(count(h, 'toast-success'), 0)
  })
}
for (const result of ['reject', 'malformed']) {
  await test(`${result} Ingress response fails closed and permits retry`, async () => {
    const h = harness({ ingress: () => { if (result === 'reject') throw new Error('private'); return { data: {} } } })
    await h.specCheck()
    assert.equal(h.specCheckFlag.value, true)
    assert.equal(h.specChecking.value, false)
    assert.equal(count(h, 'resources'), 0)
    assert.ok(h.specCheckErrors.value.length)
    assert.ok(!h.specCheckErrors.value.join().includes('private'))
    await h.specCheck()
    assert.equal(count(h, 'ingress'), 2)
  })
}
for (const proceed of [false, true]) {
  await test(`resource shortage retains existing user override=${proceed}`, async () => {
    const h = harness({ resources: () => ({ data: false }), confirm: proceed })
    await h.specCheck()
    assert.equal(h.specCheckFlag.value, !proceed)
    assert.equal(count(h, 'confirm'), 1)
  })
}
await test('duplicate clicks send only one request while checking', async () => {
  let finish
  const h = harness({ ingress: () => new Promise(resolve => { finish = resolve }) })
  const first = h.specCheck(); await h.specCheck()
  assert.equal(count(h, 'ingress'), 1)
  finish({ data: { valid: true } }); await first
})
await test('VM checks never call the K8s preflight and preserve NodeGroup checks', async () => {
  const h = harness(); h.selectInfra.value = 'VM'; h.vmTargetMode.value = 'NODE_GROUP'; h.selectVmNodeGroupId.value = 'group-a'
  await h.specCheck()
  assert.equal(count(h, 'ingress'), 0)
  assert.equal(count(h, 'vm'), 2)
  assert.equal(h.specCheckFlag.value, false)
})
await test('missing target prevents any API call', async () => {
  const h = harness(); h.selectCluster.value = ''; await h.specCheck()
  assert.equal(count(h, 'ingress'), 0)
  assert.equal(h.specCheckFlag.value, true)
})
await test('retry after correcting the route clears errors and can pass', async () => {
  let invalid = true
  const h = harness({ ingress: () => ({ data: { valid: !invalid, errors: invalid ? ['conflict'] : [] } }) })
  await h.specCheck(); h.ingressData.value.ingressPath = '/new'; invalid = false; await h.specCheck()
  assert.equal(h.specCheckFlag.value, false)
  assert.deepEqual(h.specCheckErrors.value, [])
})

assert.match(between('const runInstall =', 'const buildIngressPayload ='), /\.\.\.buildIngressPayload\(\)/)
assert.match(between('const runInstall =', 'const buildIngressPayload ='), /specCheckFlag\.value \|\| specChecking\.value/)
console.log(`Ingress preflight form tests passed (${cases} cases).`)
