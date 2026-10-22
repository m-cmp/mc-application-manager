# Server 52 catalog / Nexus validation — 2026-10-22

## Deployed behavior

| Selection | Artifact source when used |
| --- | --- |
| Built-in/default Docker package | Its existing external Registry |
| Built-in/default Helm chart | Its existing external repository or bundled chart |
| Public Registry → VM → Import & Create | The verified Docker image copied into Nexus |
| Public Registry → K8S → Import & Create | The verified Helm chart copied into Nexus; original chart image locations retained |
| Existing Package | That package's recorded source |

AM is running on `52.24.20.63` with image
`mc-application-manager:catalog-nexus-20261022.4`; its container and health check are healthy.
The deployed JAR SHA-256 is
`5192d5716bff87e3517dae3a724ed461fd29a9a1708aaf9640a46130a2224852`.

## Actual registrations and artifacts

Registration was executed through AM's real UI, using the same frontend embedded by Web Console.
Both catalogs remain available for review:

| Catalog | Catalog ID | Linked artifact |
| --- | --- | --- |
| Nexus BusyBox UI Check 20261022 | 18 | Package 12, `artifactSource=NEXUS` |
| Nexus Helm UI Check 20261022 | 19 | Helm chart 10, `artifactSource=NEXUS` |

Docker image: `52.24.20.63:5500/busybox:1.38.0-uclibc`, in Nexus Docker hosted repository `test`.
The Registry served the root index and every referenced child manifest, configuration and layer.
Verification checked 13 manifests and 24 distinct blobs, including each SHA-256 digest and size.
Root digest: `sha256:b699b0c739b2d039afd8e2488a3ccfc0b9a86301d87633448d4a93c57bee8c76`.

Helm chart: `podinfo:6.15.0`, in the new Nexus Helm hosted repository `helm-hosted`.
The downloaded Nexus archive is byte-for-byte identical to the archive fetched by Helm from
`https://stefanprodan.github.io/podinfo` for that version.
Archive SHA-256: `5ca7896889b539e04cdad4df2093ff1ff7576295e7e3ef90a1e1845e3a334d75`.

Anonymous chart download returns 401. Authenticated download succeeds. The actual
`NexusHelmChartAccess` class from the deployed JAR downloaded this private chart and passed
`helm template` in an isolated validation container. No Kubernetes release was created.
Rendered images include `ghcr.io/stefanprodan/podinfo:6.15.0` and the chart's original helper/test
images; the chart archive's image defaults were preserved.

The pre-deployment DB snapshot was compared after registration and final redeployment:
all 11 existing package rows and all 9 existing Helm chart rows retained their names,
versions, catalog bindings and original repository/image references.

## Checks

- Frontend TypeScript check and Vite production build passed.
- Final focused Java regression run: 96 tests, 0 failures, 0 errors, 0 skipped.
- Earlier full Windows run: 929 tests, 921 passed, 7 POSIX-permission failures in
  `RegistryImageCopyTest`, 1 opt-in SSH smoke test skipped.
- All 19 `RegistryImageCopyTest` cases passed on Linux on server 52, using an isolated container.
- Actual Nexus Docker manifest/blob reads, chart archive comparison, private chart download
  from the final JAR, and Helm rendering passed.

Screenshots and machine-readable results are under `build/catalog-nexus-validation/`:
`busybox-catalog.png`, `busybox-repository.png`, `helm-catalog.png`, `helm-repository.png`,
`validation-result.json`, `helm-validation-result.json`, and `private-chart-live-check.log`.

## Web Console and test boundaries

Web Console's AM HTML now uses its own origin (`https://52.24.20.63:3001/web/...`) with
`frame-ancestors 'self'`. AM API/assets remain on port 18084. Nginx, Web Console's frontend
endpoint configuration, IAM's registered frontend service URL, and the deployment environment
setting were updated together. See [integration settings](catalog-repository.md#embedding-behind-an-ip-based-web-console).

The user confirmed that MCMP displays correctly in an incognito window. The automation-accessible
normal Chrome window continued to show an empty menu iframe, while a simple same-origin frame
loaded AM successfully. The exact remaining normal-window cache/extension cause was not proved;
no browser security settings or extensions were disabled. Temporary diagnosis pages and the
experimental frontend loader modification were removed/restored.

No new VM or Kubernetes application deployment was executed. Docker's configured deployment
endpoint is the existing HTTP Registry on port 5500; target VMs must be able to reach it and
their Docker daemon must already permit that Registry transport, or use a trusted TLS Registry
endpoint instead. AM does not change VM daemon trust settings automatically.

## Backup and rollback

Protected server directory: `/home/ubuntu/am-catalog-nexus-20261022`.

- `am-before.dump`: database backup before schema changes and registration.
- `before-artifacts.json`: package/chart reference baseline.
- `before-container.json`: original container configuration (contains secrets; keep private).
- `rollback.sh`: restore the pinned original AM image.
- `integration-rollback.py`: restore Nginx, Web Console API settings, IAM frontend service URL
  and the public frontend environment setting.
- `validation-result.json`, `helm-validation-result.json`, `private-chart-live-check.log`:
  final artifact verification evidence.

AM rollback:

```sh
sh /home/ubuntu/am-catalog-nexus-20261022/rollback.sh
```

Integration rollback:

```sh
python3 /home/ubuntu/am-catalog-nexus-20261022/integration-rollback.py
```

These restore application/routing configuration. They do not delete the review catalogs,
Nexus artifacts, or the additive nullable database columns. The original SSH key was not modified.

## Follow-up after component deletion

After the validation above, the user deleted the BusyBox component from the component list.
The subsequent read-only check confirmed a successful Nexus component DELETE (204), an empty
component list, and a 404 for `busybox:1.38.0-uclibc`. All 13 digest manifests and 24 layer/config
blobs remained accessible, and catalog 18 still referenced the deleted tag.

The existing component deletion path does not coordinate Docker garbage collection, blob-store
compaction, or catalog reference handling. Dedicated Docker GC and compaction tasks were not
configured at the time of the check. The component deletion follow-up is outside this change.

## Frontend build and Nexus UI revalidation

The current frontend was rebuilt with `npm run build`, including the TypeScript check,
using the server's API and asset URLs. All 43 output files matched both the frontend
packaged in the running AM JAR and the bytes actually served by AM. The existing
`catalog-nexus-20261022.4` deployment already contains that exact build.

The Nexus UI image was rebuilt on server 52 using `scripts/nexus-ui/build.sh`.
The generated JAR matched the running Nexus JAR byte-for-byte, with SHA-256
`981e9774c9cc074587982fd5d29b8611d16e177c577e9be850b7851a51ab3c3e`.
The ExtDirect transport regression check passed. Both containers remained healthy;
the matching builds did not require a container restart.

The file manifest and verification result are saved under
`build/catalog-nexus-validation/frontend-build-manifest.json` and
`build/catalog-nexus-validation/ui-build-verification.json` locally. The server copy
is in `/home/ubuntu/am-catalog-nexus-20261022/ui-build-validation`.
