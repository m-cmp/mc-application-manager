// Run against the real Vite app: AM_UI_TEST_URL=http://127.0.0.1:4179
// PLAYWRIGHT_MODULE_PATH may point to an existing Playwright installation.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const origin = process.env.AM_UI_TEST_URL || 'http://127.0.0.1:4179'
const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const page = await browser.newPage({ viewport: { width: 1280, height: 1000 } })
const errors = []
page.on('pageerror', error => { errors.push(error.message); console.error('Browser error:', error.message) })
let state = 'FAILED', delay = false, release, deny = false, scenarios = 0
await page.route('**/*', async route => {
  const path = new URL(route.request().url()).pathname
  if (!['/api/applications/', '/applications/', '/catalog/', '/cbtumblebug/'].some(p => path.startsWith(p))) return route.continue()
  let data = path.startsWith('/catalog/') || path.startsWith('/cbtumblebug/') ? [] : null
  if (path.includes('/status/groups')) data = [{
    id: 1, deploymentHistoryId: 42, applicationName: 'Nginx', status: state,
    deploymentType: 'K8S', namespace: 'default', clusterName: 'k5', checkedAt: '2026-10-13T08:00:00'
  }]
  if (path.includes('/integrated/deployment/')) {
    if (delay) await new Promise(resolve => { release = resolve })
    if (deny) return route.fulfill({ status: 401, json: { code: 401, message: 'Unauthorized' } })
    data = { integratedInfo: {
      deploymentId: 42, catalogName: 'Nginx', status: state, applicationStatus: state === 'SUCCESS' ? 'RUNNING' : state,
      deploymentType: 'K8S', namespace: 'default', clusterName: 'k5', operationHistories: [], errorLogs: [],
      deploymentLogs: [{ id: 1, logType: state === 'SUCCESS' ? 'INFO' : 'ERROR', logMessage: 'Deployment log: ' + state }]
    } }
  }
  return route.fulfill({ json: { code: 200, data } })
})
const modal = page.locator('#application-status-detail-modal')
const openStatus = async () => {
  await page.locator('.tabulator-row').first().getByText('Nginx', { exact: true }).click()
  await modal.locator('.modal-body').waitFor({ state: 'visible' })
}
const assertClosed = async () => {
  await modal.waitFor({ state: 'hidden' })
  await page.waitForTimeout(200)
  assert.equal(await page.locator('.modal-backdrop').count(), 0)
  assert.equal(await page.evaluate(() => document.body.classList.contains('modal-open')), false)
  assert.equal(await page.evaluate(() => document.body.style.overflow), '')
  scenarios++
}
try {
  await page.goto(origin + '/web/applications/status')
  await page.locator('.tabulator-row').first().waitFor()
  // A second Bootstrap bundle/global must not own this component's lifecycle.
  await page.evaluate(() => { window.bootstrap = { Modal: class { constructor() { throw Error('Foreign Bootstrap must not be used') } } } })
  for (state of ['SUCCESS', 'FAILED', 'PARTIAL_SUCCESS']) {
    for (const action of ['footer', 'header', 'escape', 'backdrop']) {
      await openStatus()
      await modal.getByText('Deployment log: ' + state, { exact: true }).last().waitFor()
      await page.waitForTimeout(400)
      if (action === 'footer') await modal.locator('button.btn-secondary').click()
      if (action === 'header') await modal.locator('button.btn-close').click()
      if (action === 'escape') await page.keyboard.press('Escape')
      if (action === 'backdrop') await page.mouse.click(5, 5)
      await assertClosed()
    }
  }
  // Close immediately during animation/loading; a late response must not resurrect the dialog.
  delay = true
  await openStatus()
  await modal.locator('button.btn-close').click({ force: true })
  await assertClosed()
  while (!release) await page.waitForTimeout(20)
  delay = false; release()
  await page.waitForTimeout(300)
  assert(!(await modal.isVisible()))
  assert.equal(await modal.locator('.alert-danger').count(), 0)
  // A denied API is not a reason to trap the user in an empty dialog.
  deny = true
  await openStatus()
  await modal.getByText('No data available', { exact: true }).waitFor()
  await page.waitForTimeout(400)
  await modal.locator('button.btn-secondary').click()
  await assertClosed(); deny = false
  await openStatus()
  await page.waitForTimeout(400)
  await page.evaluate(async () => {
    await document.querySelector('#app').__vue_app__.config.globalProperties.$router.push('/web/applications/swcatalog')
  })
  await page.getByRole('button', { name: 'Deploy', exact: true }).first().waitFor()
  await page.waitForTimeout(600)
  assert.equal(await page.locator('.modal-backdrop').count(), 0)
  assert.equal(await page.evaluate(() => document.body.classList.contains('modal-open')), false)
  scenarios++
  // Catalog and deployment-failure entry points share the exact same detail component.
  await page.evaluate(() => {
    const seen = new Set()
    const find = vnode => {
      if (!vnode || seen.has(vnode)) return
      seen.add(vnode)
      if (vnode.component?.type?.__file?.endsWith('/softwareCatalogList.vue')) return vnode.component
      const nodes = [vnode.component?.subTree, ...(Array.isArray(vnode.children) ? vnode.children : [])]
      for (const node of nodes) { const found = find(node); if (found) return found }
    }
    const catalog = find(document.querySelector('#app').__vue_app__._instance.subTree)
    if (!catalog) throw Error('Catalog component not found')
    catalog.setupState.openApplicationDetail(42)
  })
  const catalogModal = page.locator('#application-detail-modal')
  await catalogModal.locator('.modal-body').waitFor({ state: 'visible' })
  await page.waitForTimeout(400)
  await catalogModal.locator('button.btn-secondary').click()
  await catalogModal.waitFor({ state: 'hidden' }); scenarios++
  await page.getByRole('button', { name: 'Deploy', exact: true }).first().click()
  await page.waitForTimeout(500)
  await page.evaluate(() => {
    const s = document.querySelector('#install-form').__vueParentComponent.setupState
    window.installState = s
    s.deploymentFailure = { message: 'Test failure', deploymentId: 42, unknown: false }
    s.selectMci = 'preserved-input'
  })
  for (const action of ['footer', 'escape']) {
    await page.getByRole('button', { name: 'View deployment logs' }).click()
    const detail = page.locator('#install-form-failure-detail')
    await detail.locator('.alert-danger').waitFor()
    await page.waitForTimeout(400)
    if (action === 'footer') await detail.locator('button.btn-secondary').click()
    else await page.keyboard.press('Escape')
    await page.locator('#install-form.show').waitFor()
    await page.waitForTimeout(400)
    assert.equal(await page.evaluate(() => window.installState.selectMci), 'preserved-input')
    assert.equal(await page.locator('.modal-backdrop').count(), 1)
    scenarios++
  }
  await page.locator('#install-form button.btn-close').click()
  await page.locator('#install-form').waitFor({ state: 'hidden' })
  await page.waitForTimeout(300)
  assert.equal(await page.locator('.modal-backdrop').count(), 0)
  assert.deepEqual(errors, [])
  console.log('PASS: ' + scenarios + ' real-browser modal scenarios; close/reopen, transitions, late responses, auth failure, route cleanup, catalog, failure logs and retained inputs')
} finally {
  await browser.close()
}
