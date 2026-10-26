// AM_UI_TEST_URL=http://127.0.0.1:4179 node scripts/test-cpu-metrics-display.mjs
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright')
const browser = await chromium.launch({ headless: true, channel: 'chrome' })
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
const origin = process.env.AM_UI_TEST_URL || 'http://127.0.0.1:4179'
const errors = []
page.on('pageerror', error => errors.push(error.message))
let scenario = 'normalized'
await page.route('**/*', async route => {
  const url = new URL(route.request().url()), path = url.pathname
  if (!['/api/applications/', '/applications/', '/catalog/', '/cbtumblebug/'].some(prefix => path.startsWith(prefix))) return route.continue()
  let data = path.startsWith('/catalog/') || path.startsWith('/cbtumblebug/') ? [] : null
  if (path.includes('/status/groups')) data = [{ id: 1, deploymentHistoryId: 43, applicationName: 'Grafana', status: 'RUNNING', deploymentType: 'VM', namespace: 'default', mciId: 'infra', vmId: 'g1-1' }]
  if (path.includes('/integrated/deployment/')) data = { integratedInfo: {
    deploymentId: 43, catalogName: 'Grafana', status: 'SUCCESS', applicationStatus: 'RUNNING', deploymentType: 'VM',
    namespace: 'default', mciId: 'infra', vmId: 'g1-1', executedAt: '2026-01-01 00:00:00', lastCheckedAt: '2026-10-26 08:00:00',
    cpuUsage: scenario === 'unknown' ? null : scenario === 'high' ? 85 : 40.78,
    memoryUsage: 18.45, networkIn: 110000000, networkOut: 494000, ingressEnabled: false, operationHistories: [], errorLogs: [], deploymentLogs: []
  } }
  if (path.endsWith('/operation-profile')) {
    const days = Number(url.searchParams.get('days') || 7)
    const end = new Date('2026-10-25T00:00:00Z'), start = new Date(end)
    start.setUTCDate(end.getUTCDate() - days + 1)
    data = { id: days, analysisStartDate: start.toISOString().slice(0, 10), analysisEndDate: '2026-10-25',
      cpuSizingStatus: scenario === 'unknown' ? 'UNKNOWN' : scenario === 'high' ? 'UNDER_PROVISIONED' : 'RIGHT_SIZED',
      memorySizingStatus: 'RIGHT_SIZED', selectedResourceType: 'GENERAL_PURPOSE', recommendedResourceType: scenario === 'high' ? 'CPU_INTENSIVE' : 'GENERAL_PURPOSE',
      dataStatus: days === 7 ? 'SUFFICIENT' : 'PARTIAL_DATA', validDays: 7, missingDays: days - 7,
      confidence: scenario === 'unknown' ? 0.6 : scenario === 'high' ? 0.78 : 0.75,
      reasons: JSON.stringify(['networkEvidence=maxInBytes=70776276, maxOutBytes=218574']) }
  }
  if (path.endsWith('/policy-recommendation')) data = {
    selectedResourceType: 'GENERAL_PURPOSE', recommendedResourceType: scenario === 'high' ? 'CPU_INTENSIVE' : 'GENERAL_PURPOSE',
    mismatch: scenario === 'high', confidence: scenario === 'unknown' ? 0.6 : 0.75, actions: 'NO_ACTION', message: 'Policy summary'
  }
  await route.fulfill({ json: { code: 200, data } })
})
try {
  for (scenario of ['normalized', 'high', 'unknown']) {
    await page.goto(origin + '/web/applications/status')
    await page.locator('.tabulator-row').first().getByText('Grafana', { exact: true }).click()
    const modal = page.locator('#application-status-detail-modal')
    await modal.getByText('Policy summary', { exact: true }).waitFor()
    await modal.getByText('현재 누적 수신', { exact: true }).waitFor()
    await modal.getByText('현재 누적 송신', { exact: true }).waitFor()
    await modal.getByText('최근 수집:', { exact: false }).waitFor()
    await modal.getByText('분석 기간 내 누적 수신 최댓값 67.5 MB, 누적 송신 최댓값 213.45 KB가 확인되었습니다.', { exact: true }).waitFor()
    assert.equal(await modal.locator('h5.card-title').first().innerText(), scenario === 'unknown' ? '-' : scenario === 'high' ? '85%' : '40.78%')
    await modal.getByText(scenario === 'unknown' ? '판단 보류' : scenario === 'high' ? '사용률 높음 · 증설 검토' : '적정', { exact: true }).first().waitFor()
    await modal.getByText(scenario === 'high' ? '검토 필요' : '일치', { exact: true }).waitFor()
    assert.equal(await modal.getByText('Ingress Information', { exact: true }).count(), 0)
    await modal.locator('button.btn-secondary').click()
    await modal.waitFor({ state: 'hidden' })
  }
  assert.deepEqual(errors, [])
  console.log('PASS: normalized, high-CPU and unknown-CPU actual Vue modal rendering; cumulative network labels, evidence and policy mismatch')
} finally { await browser.close() }
