import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
const source=await readFile(new URL('../src/utils/deploymentSubmission.ts',import.meta.url),'utf8')
const js=ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ES2022}}).outputText
const {submitAndTrackDeployment,DeploymentStatusUnknown,DeploymentFailed,deploymentOperationId}=await import('data:text/javascript;base64,'+Buffer.from(js).toString('base64'))
const state=(s)=>({id:'op1',namespace:'ns1',state:s,message:s,deploymentId:22})
const sleep=async()=>{}
let submits=0,polls=0
const result=await submitAndTrackDeployment('op1','ns1',async()=>{submits++;throw {response:{status:504}}},async()=>{polls++;return state(polls===1?'RUNNING':'SUCCEEDED')},()=>true,sleep)
assert.equal(result.data.id,22);assert.equal(submits,1);assert.equal(polls,2)
for(const s of ['FAILED','PARTIAL_SUCCESS']) await assert.rejects(submitAndTrackDeployment('op1','ns1',async()=>state(s),async()=>assert.fail(),()=>true,sleep),new RegExp(s))
for (const deploymentId of [22, undefined]) {
  await assert.rejects(submitAndTrackDeployment('op1','ns1',async()=>({...state('FAILED'), deploymentId}),async()=>assert.fail(),()=>true,sleep),
    error => error instanceof DeploymentFailed && error.deploymentId === deploymentId && error.message === 'FAILED')
}
await assert.rejects(submitAndTrackDeployment('op1','ns1',async()=>state('INTERRUPTED'),async()=>assert.fail(),()=>true,sleep),DeploymentStatusUnknown)
await assert.rejects(submitAndTrackDeployment('op1','ns1',async()=>state('QUEUED'),async()=>{throw Error('network')},()=>true,sleep),DeploymentStatusUnknown)
await assert.rejects(submitAndTrackDeployment('op1','ns1',async()=>state('QUEUED'),async()=>assert.fail(),()=>false,sleep),DeploymentStatusUnknown)
await assert.rejects(submitAndTrackDeployment('op1','ns1',async()=>({...state('SUCCEEDED'),id:'other'}),async()=>assert.fail(),()=>true,sleep),DeploymentStatusUnknown)
console.log('PASS: gateway504 single submit recovery, success/failure/partial propagation, interrupted/network/cancel/mismatched operation handling')

assert.match(deploymentOperationId({getRandomValues: array => array.fill(7)}), /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
console.log('PASS: HTTP-compatible operation ID uses getRandomValues only')

try {
  await submitAndTrackDeployment('op1','ns1',async()=>state('INTERRUPTED'),async()=>assert.fail(),()=>true,sleep)
  assert.fail('expected interrupted tracking')
} catch(error) { assert.deepEqual(error.interruptedOperation,{id:'op1',namespace:'ns1'}) }
let closed=false
await assert.rejects(submitAndTrackDeployment('op1','ns1',async()=>state('ABANDONED'),async()=>assert.fail(),()=>true,sleep,10,()=>{closed=true}),/ABANDONED/)
assert.equal(closed,true)
console.log('PASS: interrupted operation can be explicitly closed without reporting success')

for(const prior of ['SUCCEEDED','FAILED','PARTIAL_SUCCESS','ABANDONED']) {
  let cleared=false,starts=0
  await assert.rejects(submitAndTrackDeployment('op1','ns1',async()=>{starts++;throw {response:{status:409}}},async()=>state(prior),()=>true,sleep,10,()=>{cleared=true}),/previous operation finished/i)
  assert.equal(cleared,true);assert.equal(starts,1)
}
let cleared=false
await assert.rejects(submitAndTrackDeployment('op1','ns1',async()=>{throw {response:{status:409}}},async()=>state('RUNNING'),()=>true,sleep,10,()=>{cleared=true}),DeploymentStatusUnknown)
assert.equal(cleared,false)
console.log('PASS: changed-payload409 clears only terminal receipts and never automatically resubmits')
