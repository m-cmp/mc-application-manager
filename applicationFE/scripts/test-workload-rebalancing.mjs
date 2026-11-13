// Run with VITE_API_URL=/ and AM_UI_TEST_URL pointing to a local Vite server.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const origin = process.env.AM_UI_TEST_URL || 'http://127.0.0.1:4179'
const browser = await chromium.launch({ headless: true, channel: 'chrome' })

try {
  for (const [hpa, rebalancing] of [[false, false], [true, false], [false, true], [true, true]]) {
    const page = await browser.newPage()
    page.setDefaultTimeout(8000)
    const requests = []
    await page.route('**/*', route => {
      const url = new URL(route.request().url())
      if (url.origin !== origin) return route.abort()
      const path = url.pathname
      let data
      if (path === '/catalog/software') data = [{
        id: 1, name: 'Nginx for Kubernetes', defaultPort: 80, hpaEnabled: false,
        cpuThreshold: 60, memoryThreshold: 75, minReplicas: 1, maxReplicas: 3,
        helmChart: { chartName: 'nginx', chartVersion: '0.16.8' }
      }]
      else if (path === '/cbtumblebug/ns') data = [{ id: 'default' }]
      else if (path.endsWith('/infra')) data = [{ id: 'vm-test' }]
      else if (path.endsWith('/k8scluster')) data = [{ id: 'k8s-test', connectionConfig: { providerName: 'aws' } }]
      else if (path === '/applications/k8s/check') data = true
      else if (path === '/applications/k8s/ingress/check') data = { valid: true, warnings: [] }
      else if (path === '/applications/deployment-submissions/K8S') {
        requests.push(route.request().postDataJSON())
        data = { id: route.request().headers()['idempotency-key'], namespace: 'default', state: 'SUCCEEDED' }
      }
      else if (path.startsWith('/applications/') || path.startsWith('/catalog/')) data = []
      else return route.continue()
      return route.fulfill({ json: { code: 200, data } })
    })
    try {
      await page.goto(origin + '/web/applications/swcatalog')
      await page.getByRole('button', { name: 'Deploy', exact: true }).first().click()
      const form = page.locator('#install-form')
      await form.locator('#infra').selectOption('K8S')
      await form.locator('#k8s-cluster option[value="k8s-test"]').waitFor({ state: 'attached' })
      await form.locator('#k8s-cluster').selectOption('k8s-test')
      const chart = form.locator('select').filter({ has: page.locator('option[value="Nginx for Kubernetes"]') })
      await chart.selectOption('Nginx for Kubernetes')
      const rb = form.locator('#rebalancingCpuThreshold')
      assert.equal(await rb.count(), 0)
      await form.locator('#hpaEnabled').setChecked(hpa)
      await form.locator('#workloadRebalancingEnabled').setChecked(rebalancing)
      if (rebalancing) {
        await rb.fill('0')
        await form.getByRole('button', { name: 'Spec Check' }).click()
        await page.getByText('Enter Workload Rebalancing CPU and memory thresholds', { exact: false }).waitFor()
        assert.equal(await form.getByRole('button', { name: 'Deploy', exact: true }).isDisabled(), true)
        await rb.fill('90.5')
        await form.locator('#rebalancingMemoryThreshold').fill('80')
        await form.locator('#workloadRebalancingEnabled').uncheck()
        assert.equal(await rb.count(), 0)
        await form.locator('#workloadRebalancingEnabled').check()
        assert.equal(await rb.inputValue(), '90.5')
      } else assert.equal(await rb.count(), 0)
      await form.getByRole('button', { name: 'Spec Check' }).click()
      try {
        await Promise.all([
          form.getByRole('button', { name: 'Deploy', exact: true }).click(),
          page.waitForResponse(res => res.url().endsWith('/applications/deployment-submissions/K8S'))
        ])
      } catch (error) {
        throw new Error(await page.locator('body').innerText(), { cause: error })
      }
      assert.equal(requests.length, 1)
      assert.equal(requests[0].hpaEnabled, hpa)
      assert.equal(requests[0].workloadRebalancingEnabled, rebalancing)
      assert.equal(requests[0].cpuThreshold, 60)
      assert.equal(requests[0].memoryThreshold, 75)
      assert.equal(requests[0].rebalancingCpuThreshold, rebalancing ? 90.5 : undefined)
      assert.equal(requests[0].rebalancingMemoryThreshold, rebalancing ? 80 : undefined)
      console.log(`HPA=${hpa}, Workload Rebalancing=${rebalancing}: PASS`)
    } finally {
      await page.close()
    }
  }
} finally {
  await browser.close()
}
