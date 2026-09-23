import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import * as Vue from 'vue'
import { compile } from '@vue/compiler-dom'
import { renderToString } from '@vue/server-renderer'
import ts from 'typescript'

const transpile = source => ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 }
}).outputText
const policy = await readFile(new URL('../src/utils/vmClustering.ts', import.meta.url), 'utf8')
const { isVmClusteringCatalog } = await import(
  `data:text/javascript;base64,${Buffer.from(transpile(policy)).toString('base64')}`)
const catalog = (name, packageName) => ({ name, packageInfo: { packageName } })
const cases = [
  [catalog('Redis', 'redis'), true],
  [catalog(' Redis ', 'library/redis:7.4'), true],
  [catalog('Redis 7', 'docker.io/library/redis'), true],
  [catalog('redis', 'registry.example:5000/cache/redis'), true],
  [catalog('REDIS', 'redis@sha256:abc123'), true],
  [catalog('Elasticsearch', 'elasticsearch'), true],
  [catalog('Elasticsearch 8', 'docker.elastic.co/elasticsearch/elasticsearch'), true],
  [catalog('ELASTICSEARCH', 'library/elasticsearch:8.17.0'), true],
  [catalog('Nginx', 'nginx'), false],
  [catalog('Grafana', 'grafana/grafana'), false],
  [catalog('Jenkins', 'jenkins/jenkins'), false],
  [catalog('Rclone GUI', 'rclone/rclone'), false],
  [catalog('Redis Exporter', 'oliver006/redis_exporter'), false],
  [catalog('Redis Insight', 'redis/redisinsight'), false],
  [catalog('Elasticsearch Exporter', 'prometheuscommunity/elasticsearch-exporter'), false],
  [catalog('Redis', 'nginx'), false],
  [catalog('Elasticsearch', 'redis'), false],
  [catalog('Redis Elasticsearch', 'redis'), false],
  [catalog('My cache', 'redis'), false], // Existing backend cannot pick Redis ports from this label.
  [catalog('Notredis', 'redis'), false],
  [{ name: 'Redis' }, false],
  [{ name: 'Redis', packageInfo: null }, false],
  [null, false],
  [undefined, false]
]
for (const [value, expected] of cases) assert.equal(isVmClusteringCatalog(value), expected, JSON.stringify(value))

const form = await readFile(new URL('../src/views/softwareCatalog/components/applicationInstallationForm.vue', import.meta.url), 'utf8')
const start = form.indexOf('const canSelectClustering = computed(')
const end = form.indexOf('\nconst selectedClusterProvider', start)
assert.ok(start > 0 && end > start)
const makeAvailability = new Function('computed', 'watch', 'selectInfra', 'vmTargetMode',
  'isTargetLocked', 'selectedCatalogInfo', 'selectDeploymentType', 'isVmClusteringCatalog',
  transpile(form.slice(start, end)) + '\nreturn canSelectClustering;')
const radio = form.match(/<div class="form-check" v-if="canSelectClustering">[\s\S]*?<\/div>/)?.[0]
assert.ok(radio, 'The actual Clustering radio must use the eligibility condition')
const render = new Function('Vue', compile(radio, { mode: 'function', prefixIdentifiers: true }).code)(Vue)
let renderedCases = 0
for (const [value, eligible] of cases) {
  for (const infra of ['VM', 'K8S', '']) {
    for (const target of ['VM', 'NODE_GROUP']) {
      for (const locked of [false, true]) {
        const scope = Vue.effectScope()
        const selection = Vue.ref('Standalone')
        const available = scope.run(() => makeAvailability(Vue.computed, Vue.watch,
          Vue.ref(infra), Vue.ref(target), Vue.ref(locked), Vue.ref(value), selection, isVmClusteringCatalog))
        const expected = eligible && infra === 'VM' && target === 'VM' && !locked
        assert.equal(available.value, expected)
        const html = await renderToString(Vue.createSSRApp({
          setup: () => ({ canSelectClustering: available, selectDeploymentType: selection, isTargetLocked: locked }), render
        }))
        assert.equal(html.includes('id="Clustering"'), expected)
        scope.stop()
        renderedCases++
      }
    }
  }
}

for (const change of ['catalog', 'target', 'locked', 'infra']) {
  const scope = Vue.effectScope()
  const infra = Vue.ref('VM'), target = Vue.ref('VM'), locked = Vue.ref(false)
  const info = Vue.ref(catalog('Redis', 'redis')), selection = Vue.ref('Clustering')
  scope.run(() => makeAvailability(Vue.computed, Vue.watch, infra, target, locked, info, selection, isVmClusteringCatalog))
  if (change === 'catalog') info.value = catalog('Grafana', 'grafana/grafana')
  if (change === 'target') target.value = 'NODE_GROUP'
  if (change === 'locked') locked.value = true
  if (change === 'infra') infra.value = 'K8S'
  assert.equal(selection.value, 'Standalone', `Reset synchronously after ${change} changes`)
  scope.stop()
}

// Exercise the actual submit guard, including a stale/modified hidden selection.
const submitStart = form.indexOf('const runInstall = async () => {') + 'const runInstall = async () => {'.length
const guardEnd = form.indexOf("\n  if (modalTitle.value === 'Application Installation' && (specCheckFlag", submitStart)
assert.ok(guardEnd > submitStart)
const submitGuard = new Function('modalTitle', 'selectInfra', 'selectDeploymentType', 'canSelectClustering', 'toast',
  // The separate provider guard is unrelated to clustering eligibility.
  'const deploying = { value: false }, deploymentCompleted = { value: false }, jupyterInstallationUnsupported = { value: false };\n' + transpile(form.slice(submitStart, guardEnd)) + '\nreturn "continue";')
const installation = { value: 'Application Installation' }
let errors = 0
assert.equal(submitGuard(installation, { value: 'VM' }, { value: 'Clustering' }, { value: false }, { error: () => errors++ }), undefined)
assert.equal(errors, 1)
assert.equal(submitGuard(installation, { value: 'VM' }, { value: 'Standalone' }, { value: false }, {}), 'continue')
assert.equal(submitGuard(installation, { value: 'VM' }, { value: 'Clustering' }, { value: true }, {}), 'continue')
assert.equal(submitGuard({ value: 'Application Uninstallation' }, { value: 'VM' }, { value: 'Clustering' }, { value: false }, {}), 'continue')
console.log(`VM clustering passed: ${cases.length} catalog cases, ${renderedCases} rendered visibility cases, 4 transitions, 4 submit cases.`)
