import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'
import * as Vue from 'vue'
import { compile } from '@vue/compiler-dom'
import { renderToString } from '@vue/server-renderer'

const source = await readFile(new URL('../src/views/softwareCatalog/components/applicationInstallationForm.vue', import.meta.url), 'utf8')
const template = source.slice(0, source.indexOf('<script'))
const start = source.indexOf('const buildIngressPayload =')
const end = source.indexOf('const specCheck =', start)
const code = ts.transpileModule(source.slice(start,end)+'\nreturn buildIngressPayload;',
  {compilerOptions:{target:ts.ScriptTarget.ES2022}}).outputText
let cases=0
async function test(name,fn){await fn();cases++;console.log('PASS '+name)}

for(const ibm of [true,false])for(const tls of [true,false,undefined])for(const host of ['rclone.test.com','app.cluster.containers.appdomain.cloud']){
  await test('HTTP-only IBM payload, existing non-IBM TLS preserved '+JSON.stringify({ibm,tls,host}),()=>{
    const data={ingressEnabled:true,ingressHost:host,ingressPath:'/',ingressTlsEnabled:tls,ingressTlsSecret:'catalog-cert'}
    const build = new Function('ingressData','isIbmCluster','effectiveIngressClass','normalizeIngressHost',code)(
      Vue.ref(data),Vue.ref(ibm),Vue.ref(ibm?'public-iks-k8s-nginx':'nginx'),value=>value)
    const payload=build()
    assert.equal(payload.ingressTlsEnabled,ibm?false:tls)
    assert.equal(payload.ingressTlsSecret,ibm?null:'catalog-cert')
    assert.equal(payload.ingressHost,host)
    assert.deepEqual(data,{ingressEnabled:true,ingressHost:host,ingressPath:'/',ingressTlsEnabled:tls,ingressTlsSecret:'catalog-cert'})
  })
}
await test('cluster switching cannot accidentally enable IBM HTTPS',()=>{
  const ibm=Vue.ref(false), data=Vue.ref({ingressEnabled:true,ingressHost:'chosen.example.com',ingressTlsEnabled:true,ingressTlsSecret:'existing-cert'})
  const build = new Function('ingressData','isIbmCluster','effectiveIngressClass','normalizeIngressHost',code)(data,ibm,Vue.ref('nginx'),value=>value)
  assert.equal(build().ingressTlsEnabled,true)
  ibm.value=true;assert.equal(build().ingressTlsEnabled,false);assert.equal(build().ingressTlsSecret,null)
  ibm.value=false;assert.equal(build().ingressTlsEnabled,true);assert.equal(build().ingressTlsSecret,'existing-cert')
})
await test('protocol control, TLS discovery and preparation text are absent',()=>{
  for(const removed of ['ibmIngressProtocol','loadIbmTlsSettings','k8sIngressTlsSettings','ingressPreparationMessage','Use IBM domain','Loading managed HTTPS'])assert.ok(!source.includes(removed),removed)
  assert.ok(template.includes('HTTP via the IBM-managed load balancer.'))
  assert.ok(template.includes('HTTP does not require a certificate and is not encrypted.'))
})

// Render the actual footer for both modal and embedded modes, not a duplicate UI.
const footerStart=template.indexOf('<div\n          class="modal-footer')
const footerEnd=template.indexOf('\n      </div>',footerStart)
assert.ok(footerStart>0&&footerEnd>footerStart)
const render=new Function('Vue',compile(template.slice(footerStart,footerEnd),{mode:'function',prefixIdentifiers:true}).code)(Vue)
for(const embedded of [false,true])for(const completed of [false,true])for(const busy of [false,true]){
  await test('footer renders completed/busy state '+JSON.stringify({embedded,completed,busy}),async()=>{
    const html=await renderToString(Vue.createSSRApp({setup:()=>({
      embedded,deploymentCompleted:completed,deploying:busy,modalTitle:'Application Installation',
      shouldRunObjectStorageCheck:false,objectStorageChecking:false,objectStorageCheckPassed:true,
      specChecking:false,specCheckFlag:false,projectScopeError:'',deployDisabled:completed||busy,
      handleCancel(){},viewAppsStatus(){},runObjectStorageCheck(){},specCheck(){},runInstall(){}
    }),render}))
    assert.equal(html.includes('View Apps Status'),completed)
    assert.equal(html.includes('Spec Check'),!completed)
    assert.equal(html.includes('Deploying…'),busy&&!completed)
    if(completed){assert.ok(html.includes('Close'));assert.ok(!html.includes('>Deploy<'));assert.ok(!html.includes('>Cancel<'))}
    const deployButton=html.match(/<button[^>]*>\s*(?:Deploy|Deploying…)\s*<\/button>/)?.[0]
    if(!completed){assert.ok(deployButton);assert.ok(!deployButton.includes('data-bs-dismiss'));assert.equal(deployButton.includes('disabled'),busy)}
  })
}
await test('completed and busy forms cannot edit deployment inputs',()=>{
  assert.match(template,/<fieldset v-show="!deploymentCompleted" :disabled="deploying \|\| deploymentCompleted"/)
  assert.match(template,/<div v-if="deploymentCompleted"[^>]*role="status"/)
})
console.log('IBM HTTP and completion UI tests passed ('+cases+' cases).')
