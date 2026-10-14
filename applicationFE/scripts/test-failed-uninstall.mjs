import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import { parse, compileScript, compileTemplate } from '@vue/compiler-sfc'

const source = await readFile(new URL('../src/views/softwareCatalog/applicationStatusDisplay.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText
const { isApplicationActionDisabledStatus: lifecycleDisabled, isApplicationUninstallDisabledStatus: uninstallDisabled } = await import('data:text/javascript;base64,' + Buffer.from(js).toString('base64'))

for (const status of ['FAILED', 'failed', ' Failed ']) {
  assert.equal(lifecycleDisabled(status), true, status)
  assert.equal(uninstallDisabled(status), false, status)
}
for (const status of ['PREPARING_RUNTIME', 'PREPARING_METRICS_SERVER', 'PREPARING_INGRESS_NGINX', 'DEPLOYING', 'IN_PROGRESS', 'INSTALL', 'START', 'STARTING', 'RESTART', 'RESTARTING', 'UNINSTALL', 'UNINSTALLED']) {
  assert.equal(lifecycleDisabled(status), true, status)
  assert.equal(uninstallDisabled(status), true, status)
}
for (const status of ['RUNNING', 'SUCCESS', 'STOPPED', 'ERROR', 'IMAGE_PULL_ERROR', 'DELETE_PENDING', 'UNKNOWN', '', null, undefined]) {
  assert.equal(uninstallDisabled(status), lifecycleDisabled(status), String(status))
}
const component = await readFile(new URL('../src/views/softwareCatalog/components/applicationStatusList.vue', import.meta.url), 'utf8')
assert.match(component, /btnFlag === 'uninstall-btn'\s*\? isApplicationUninstallDisabledStatus\(status\)/)
assert.match(component, /id='uninstall-btn'[\s\S]*?\$\{uninstallDisabledAttr\}/)
assert.match(component, /id='rating-btn'[\s\S]*?\$\{disabledAttr\}/)
const { descriptor, errors } = parse(component)
assert.deepEqual(errors, [])
compileScript(descriptor, { id: 'failed-uninstall-regression' })
const template = compileTemplate({ source: descriptor.template.content, filename: 'applicationStatusList.vue', id: 'failed-uninstall-regression' })
assert.deepEqual(template.errors, [])
console.log('PASS: failed cleanup enabled; lifecycle/rating and in-progress/uninstalled guards preserved across 26 varied inputs')
