import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { Buffer } from 'node:buffer'
import ts from 'typescript'

const source = await readFile(new URL('../src/integration/installTarget.ts', import.meta.url), 'utf8')
const transpiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022
  }
}).outputText
const moduleUrl = `data:text/javascript;base64,${Buffer.from(transpiled).toString('base64')}`
const { parseInstallTarget, resolveInstallVmTarget } = await import(moduleUrl)
const generatedId = () => 'generated-request-id'

const validVm = parseInstallTarget(
  '?targetType=VM&mciId=mci-01&vmId=vm-01&requestId=req-01',
  generatedId
)
assert.equal(validVm.ok, true)
assert.deepEqual(validVm.target, {
  requestId: 'req-01',
  targetType: 'VM',
  mciId: 'mci-01',
  vmId: 'vm-01',
  nodeGroupId: '',
  clusterId: ''
})

const validVmWithNodeGroup = parseInstallTarget(
  '?targetType=VM&mciId=mci-01&nodeGroupId=group-01&vmId=vm-01',
  generatedId
)
assert.equal(validVmWithNodeGroup.ok, true)
assert.equal(validVmWithNodeGroup.target.nodeGroupId, 'group-01')
assert.equal(validVmWithNodeGroup.target.vmId, 'vm-01')

const validNodeGroup = parseInstallTarget(
  '?targetType=VM&mciId=mci-01&nodeGroupId=group-01',
  generatedId
)
assert.equal(validNodeGroup.ok, true)
assert.equal(validNodeGroup.target.nodeGroupId, 'group-01')
assert.equal(validNodeGroup.target.vmId, '')

const validK8s = parseInstallTarget('?targetType=k8s&clusterId=cluster-01', generatedId)
assert.equal(validK8s.ok, true)
assert.equal(validK8s.target.targetType, 'K8S')
assert.equal(validK8s.target.requestId, 'generated-request-id')

const invalidCases = [
  ['', 'targetType must be VM or K8S.'],
  ['?targetType=VM&mciId=mci-01', 'vmId or nodeGroupId is required.'],
  ['?targetType=VM&mciId=mci-01&vmId=vm-01&clusterId=cluster-01', 'clusterId cannot be used'],
  ['?targetType=K8S', 'clusterId is required.'],
  ['?targetType=K8S&clusterId=cluster-01&mciId=mci-01', 'mciId and vmId cannot be used'],
  ['?targetType=K8S&clusterId=bad/path', 'unsupported path characters'],
  ['?targetType=VM&mciId=mci-01&mciId=mci-02&vmId=vm-01', 'must be provided only once'],
  ['?targetType=VM&mciId=mci-01&vmId=vm-01&requestId=one&requestId=two', 'must be provided only once'],
  ['?targetType=VM&mciId=mci-01&vmId=vm-01&namespaceId=other', 'must not be supplied in the URL'],
  ['?targetType=K8S&clusterId=cluster-01&nodeGroupId=group-01', 'nodeGroupId cannot be used with a K8S target'],
  ['?targetType=VM&mciId=mci-01&nodeGroupId=group-01&nodeGroupId=group-02', 'must be provided only once'],
  ['?targetType=VM&mciId=mci-01&nodeGroupId=bad/path', 'unsupported path characters'],
  ['?targetType=K8S&clusterId=cluster-01&accessToken=secret', 'Unsupported query parameter'],
  [`?targetType=K8S&clusterId=${'x'.repeat(201)}`, '200 characters or fewer']
]

for (const [query, expectedMessage] of invalidCases) {
  const result = parseInstallTarget(query, generatedId)
  assert.equal(result.ok, false, `Expected invalid query: ${query}`)
  assert.match(result.error, new RegExp(expectedMessage.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
}

const vms = [
  { id: 'vm-a', subGroupId: 'group-a', status: 'Running' },
  { id: 'vm-b', subGroupId: 'group-a', status: 'RUNNING' },
  { id: 'vm-stopped', subGroupId: 'group-a', status: 'Stopped' },
  { id: 'vm-other', subGroupId: 'group-b', status: 'Running' },
  { id: 'vm-no-running', subGroupId: 'group-stopped', status: 'Stopped' },
  { name: 'vm-by-name', subGroupId: 'group-a', status: 'running' }
]

const legacyVmTarget = resolveInstallVmTarget(vms, { vmId: 'VM-A', nodeGroupId: '' })
assert.equal(legacyVmTarget.ok, true)
assert.equal(legacyVmTarget.mode, 'VM')
assert.equal(legacyVmTarget.vmId, 'vm-a')

const verifiedVmTarget = resolveInstallVmTarget(vms, { vmId: 'vm-b', nodeGroupId: 'group-a' })
assert.equal(verifiedVmTarget.ok, true)
assert.equal(verifiedVmTarget.mode, 'VM')
assert.equal(verifiedVmTarget.nodeGroupId, 'group-a')

const mismatchedVmTarget = resolveInstallVmTarget(vms, { vmId: 'vm-b', nodeGroupId: 'group-b' })
assert.equal(mismatchedVmTarget.ok, false)
assert.match(mismatchedVmTarget.error, /does not belong/)

const groupTarget = resolveInstallVmTarget(vms, { vmId: '', nodeGroupId: 'group-a' })
assert.equal(groupTarget.ok, true)
assert.equal(groupTarget.mode, 'NODE_GROUP')
assert.deepEqual(groupTarget.runningVmIds, ['vm-a', 'vm-b', 'vm-by-name'])
assert.equal(groupTarget.members.length, 4)

const missingVmTarget = resolveInstallVmTarget(vms, { vmId: 'missing', nodeGroupId: '' })
assert.equal(missingVmTarget.ok, false)
assert.match(missingVmTarget.error, /was not found/)

const missingGroupTarget = resolveInstallVmTarget(vms, { vmId: '', nodeGroupId: 'missing' })
assert.equal(missingGroupTarget.ok, false)
assert.match(missingGroupTarget.error, /was not found/)

const stoppedGroupTarget = resolveInstallVmTarget(vms, { vmId: '', nodeGroupId: 'group-stopped' })
assert.equal(stoppedGroupTarget.ok, false)
assert.match(stoppedGroupTarget.error, /no running VMs/)

const iframe = await readFile(new URL('../src/views/softwareCatalog/InstallSoftwareIframe.vue', import.meta.url), 'utf8')
const form = await readFile(new URL('../src/views/softwareCatalog/components/applicationInstallationForm.vue', import.meta.url), 'utf8')
assert.match(iframe, /:target-node-group-id="targetResult\.target\.nodeGroupId"/)
assert.match(form, /resolveInstallVmTarget\(availableVms/)
assert.match(form, /vmTargetMode\.value = 'NODE_GROUP'/)
assert.match(form, /vmNodeGroupId: isNodeGroupDeployment/)
assert.match(form, /isTargetLocked\.value && props\.targetNodeGroupId/)

console.log(`Install target integration passed: ${invalidCases.length + 4} parser cases, 7 VM resolution cases, iframe-to-deploy wiring.`)
