import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { ref, computed, watch, nextTick } from 'vue'
import ts from 'typescript'
const source = await readFile(new URL('../src/views/softwareCatalog/components/applicationInstallationForm.vue', import.meta.url), 'utf8')
function between(start, end) {
  const first = source.indexOf(start), last = source.indexOf(end, first)
  assert.ok(first >= 0 && last > first)
  return source.slice(first, last)
}
const code = ts.transpileModule([
  between('const isBuiltInPersistentCatalog =', 'const isJupyterObjectStorageCatalog ='),
  between('const supportsStorageClassConfig =', 'const objectStorageEndpointPlaceholder ='),
  between('function buildK8sAdditionalConfig()', 'function validateStorageClassSelection()'),
  'return { isBuiltInPersistentCatalog, supportsStorageClassConfig, storageClassRequired, storageClassErrorMessage, buildK8sAdditionalConfig, applyBuiltInPersistentDefaults };'
].join('\n'), { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
function harness() {
  const state = Object.fromEntries(Object.entries({
    selectInfra: 'K8S', selectedCatalogInfo: {}, selectedCatalogChartName: '', isJupyterObjectStorageCatalog: false,
    isLokiCatalog: false, ingressData: { ingressEnabled: true }, hpaData: { hpaEnabled: true, hpaMinReplicas: 3 },
    workloadRebalancingEnabled: true, selectedStorageClass: 'standard', storageClassList: [{name: 'standard'}],
    storageClassLoading: false, storageClassLoadError: false, storageClassFailure: '', notebookStorageGi: 10,
    selectedStorageMinimum: 1, modalTitle: 'Application Installation', showObjectStorageConfig: false, objectStorageData: {enabled:false}
  }).map(([key,value]) => [key, ref(value)]))
  const env = {...state, computed, watch, hasCatalogCapability: () => false,
    _: {isEmpty: value => !value?.length}, buildObjectStorageConfig: () => {throw new Error('Unexpected object storage')}}
  return {...state,...new Function(...Object.keys(env),code)(...Object.values(env))}
}
let cases=0
for (const app of ['redis','mariadb','postgresql','apache','tomcat']) {
  for (const target of ['VM','K8S']) {
    const h=harness()
    h.selectInfra.value=target
    h.selectedCatalogChartName.value=app
    h.selectedCatalogInfo.value={helmChart:{chartName:app,repositoryName:'mcmp-builtin',chartRepositoryUrl:'classpath:helm',chartVersion:'0.1.0',packageId:'mcmp-builtin-'+app}}
    await nextTick()
    const persistent=target==='K8S' && ['redis','mariadb','postgresql'].includes(app)
    assert.equal(h.isBuiltInPersistentCatalog.value,persistent)
    assert.equal(h.storageClassRequired.value,persistent)
    if (persistent) {
      assert.equal(h.ingressData.value.ingressEnabled,false)
      assert.equal(h.hpaData.value.hpaEnabled,false)
      assert.equal(h.workloadRebalancingEnabled.value,false)
      assert.deepEqual(h.buildK8sAdditionalConfig(),{storageClass:'standard',storageSize:'10Gi',storageAccessMode:'ReadWriteOnce'})
      for (const size of [0,-1,1.5,10000]) {
        h.notebookStorageGi.value=size
        assert.match(h.storageClassErrorMessage.value,/whole-number/)
        cases++
      }
      h.notebookStorageGi.value=20; h.selectedStorageMinimum.value=20
      assert.equal(h.storageClassErrorMessage.value,'')
      h.notebookStorageGi.value=10
      assert.match(h.storageClassErrorMessage.value,/20/)
      h.selectedStorageClass.value=''
      assert.match(h.storageClassErrorMessage.value,/StorageClass/)
      h.selectedCatalogInfo.value.helmChart.packageId='custom'
      assert.equal(h.isBuiltInPersistentCatalog.value,false)
    } else {
      assert.equal(h.ingressData.value.ingressEnabled,true)
      assert.equal(h.hpaData.value.hpaEnabled,true)
      assert.equal(h.buildK8sAdditionalConfig(),undefined)
    }
    cases++
  }
}
console.log(`Built-in Helm form passed (${cases} scenarios plus storage and identity checks).`)
