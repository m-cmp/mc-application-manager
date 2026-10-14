/** getRandomValues remains available on HTTP origins where subtle/randomUUID are unavailable. */
export function deploymentOperationId(random: Pick<Crypto, 'getRandomValues'> = globalThis.crypto): string {
  const bytes = random.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export interface DeploymentSubmissionStatus {
  id: string
  namespace: string
  state: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL_SUCCESS' | 'FAILED' | 'INTERRUPTED' | 'ABANDONED'
  message: string
  deploymentId?: number
}

export class DeploymentStatusUnknown extends Error {
  readonly deploymentStatusUnknown = true
  constructor(message: string, readonly interruptedOperation?: { id: string, namespace: string }) { super(message) }
}

export class DeploymentFailed extends Error {
  constructor(message: string, readonly deploymentId?: number, readonly state = 'FAILED') { super(message) }
}

/** A transport failure is not an installation failure. Never automatically submit a second operation. */
export async function submitAndTrackDeployment(
  id: string,
  namespace: string,
  submit: () => Promise<DeploymentSubmissionStatus>,
  getStatus: () => Promise<DeploymentSubmissionStatus>,
  isCurrent: () => boolean = () => true,
  sleep: () => Promise<void> = () => new Promise(resolve => setTimeout(resolve, 2000)),
  maxPolls = 3600,
  terminal: () => void = () => {}
): Promise<{ data: { id?: number, status: string } }> {
  let status: DeploymentSubmissionStatus | undefined
  try { status = await submit() } catch (error: any) {
    const http = error?.response?.status
    if (http === 409) {
      let previous: DeploymentSubmissionStatus | undefined
      try { previous = await getStatus() } catch { throw error }
      if (previous.id !== id || previous.namespace !== namespace) throw new DeploymentStatusUnknown('Existing operation did not match this target. Check Apps Status.')
      if (['SUCCEEDED', 'FAILED', 'PARTIAL_SUCCESS', 'ABANDONED'].includes(previous.state)) {
        terminal()
        throw new Error('The previous operation finished. Review Apps Status, then click Deploy again if you intend to use the changed settings. No new installation was started.')
      }
      if (previous.state === 'INTERRUPTED') throw new DeploymentStatusUnknown(previous.message, { id, namespace })
      throw new DeploymentStatusUnknown('An installation with the previous settings is still active. Check Apps Status before changing settings; no new installation was started.')
    }
    if (http && http >= 400 && http < 500 && http !== 408 && http !== 429) throw error
    // Submission might have committed despite a gateway timeout: recover by its original ID.
  }
  let failures = 0
  for (let attempt = 0; attempt < maxPolls; attempt++) {
    if (!isCurrent()) throw new DeploymentStatusUnknown('Tracking stopped because the form or project changed. Installation may continue; check Apps Status.')
    if (status) {
      if (status.id !== id || status.namespace !== namespace) throw new DeploymentStatusUnknown('Deployment response did not match this operation. Check Apps Status before retrying.')
      if (status.state === 'SUCCEEDED') { terminal(); return { data: { id: status.deploymentId, status: 'SUCCESS' } } }
      if (status.state === 'FAILED' || status.state === 'PARTIAL_SUCCESS' || status.state === 'ABANDONED') { terminal(); throw new DeploymentFailed(status.message || 'Installation failed. Open Apps Status for details.', status.deploymentId, status.state) }
      if (status.state === 'INTERRUPTED') throw new DeploymentStatusUnknown(status.message, { id, namespace })
      if (!['QUEUED', 'RUNNING'].includes(status.state)) throw new DeploymentStatusUnknown('Unknown deployment state. Check Apps Status before retrying.')
    }
    await sleep()
    if (!isCurrent()) throw new DeploymentStatusUnknown('Tracking stopped. Closing the dialog does not cancel the installation; check Apps Status.')
    try { status = await getStatus(); failures = 0 } catch {
      if (++failures >= 5) throw new DeploymentStatusUnknown('Could not retrieve installation status. The operation may still be running. Check Apps Status or retry tracking; do not start a second installation.')
    }
  }
  throw new DeploymentStatusUnknown('Installation is taking longer than expected. Check Apps Status; no replacement installation was submitted.')
}
