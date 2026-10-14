export interface NhnCinderAddonStatus {
  id: string
  namespace: string
  clusterName: string
  state: 'QUEUED' | 'RUNNING' | 'READY' | 'FAILED'
  code: string
  message: string
}

/** Poll the submitted job only; refresh/retry must not submit another NKS mutation automatically. */
export async function waitForNhnCinderAddon(
  initial: NhnCinderAddonStatus,
  target: { namespace: string, clusterName: string },
  getStatus: (id: string) => Promise<NhnCinderAddonStatus>,
  onProgress: (message: string) => void,
  isCurrent: () => boolean,
  sleep: () => Promise<void> = () => new Promise(resolve => setTimeout(resolve, 3000)),
  maxPolls = 1200
): Promise<void> {
  let status = initial
  const id = initial?.id
  for (let attempt = 0; attempt <= maxPolls; attempt++) {
    if (!isCurrent()) throw new Error('The form or Project changed. Refresh Cinder status on the selected cluster.')
    if (!id || status?.id !== id || status.namespace !== target.namespace || status.clusterName !== target.clusterName)
      throw new Error('Cinder preparation returned a different target. Refresh the selected cluster.')
    onProgress(status.message || 'Preparing Cinder CSI…')
    if (status.state === 'READY') return
    if (status.state === 'FAILED') throw new Error(status.message || 'Cinder preparation failed.')
    if (!['QUEUED', 'RUNNING'].includes(status.state)) throw new Error('Unknown Cinder preparation state.')
    if (attempt === maxPolls) break
    await sleep()
    if (!isCurrent()) throw new Error('The form or Project changed. Refresh Cinder status on the selected cluster.')
    status = await getStatus(id)
  }
  throw new Error('Cinder verification is taking longer than expected. Refresh status before retrying.')
}
