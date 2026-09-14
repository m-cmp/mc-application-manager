export interface IngressPreparationStatus {
  id: string
  namespace: string
  clusterName: string
  state: 'QUEUED' | 'RUNNING' | 'READY' | 'FAILED'
  message: string
}

/** Poll an existing job only: a failed/lost response must never start another shared-LB change. */
export async function waitForIngressPreparation(
  initial: IngressPreparationStatus,
  target: { namespace: string, clusterName: string },
  getStatus: (id: string) => Promise<IngressPreparationStatus>,
  onProgress: (message: string) => void,
  isCurrent: () => boolean,
  sleep: () => Promise<void> = () => new Promise(resolve => setTimeout(resolve, 3000)),
  maxPolls = 3000
): Promise<void> {
  let status = initial
  const id = initial?.id
  for (let attempt = 0; attempt <= maxPolls; attempt++) {
    if (!isCurrent()) throw new Error('The form or Project changed. Preparation may continue, but no application will be deployed from this request.')
    if (!id || status?.id !== id || status.namespace !== target.namespace || status.clusterName !== target.clusterName) {
      throw new Error('Ingress preparation returned a different target. Deployment stopped.')
    }
    onProgress(status.message || 'Preparing IBM Ingress and HTTPS…')
    if (status.state === 'READY') return
    if (status.state === 'FAILED') throw new Error(status.message || 'Ingress preparation failed.')
    if (!['QUEUED', 'RUNNING'].includes(status.state)) throw new Error('Unknown Ingress preparation state.')
    if (attempt === maxPolls) break
    await sleep()
    if (!isCurrent()) throw new Error('The form or Project changed. Check the existing preparation before retrying Deploy.')
    status = await getStatus(id)
  }
  throw new Error('Ingress preparation is taking longer than expected. Check cluster status before retrying; no application was deployed.')
}
