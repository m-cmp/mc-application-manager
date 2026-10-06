# NHN managed Cinder CSI installation

AM can install `cinder-csi-plugin` directly through NHN NKS for an NHN Kubernetes cluster. Tumblebug continues to locate the selected cluster and its cloud connection. AM reads the existing NHN credential from OpenBao, obtains a project-scoped IaaS token, and uses the NKS endpoint in that token's regional service catalog.

The installation form's NHN storage setup now offers **Install Cinder CSI add-on**. It waits for the NKS installation to complete and for `cinder.csi.openstack.org` to be registered on all CSI nodes. The user can then create the existing NHN StorageClass or select an existing one and deploy Jupyter. An existing StorageClass does not bypass the CSI readiness check. A working manual or managed CSI installation is reused without reading cloud credentials or changing add-ons.

## Configuration

The feature defaults to disabled. Add server-side configuration for the specific project namespace and Tumblebug connection. This binds the existing credential; it does not register another credential or expose it to the browser.

```yaml
app:
  nhn-cinder-addon:
    enabled: true
    open-bao-url: http://openbao:8200
    open-bao-token-file: /run/secrets/am-nhn-openbao-token
    ready-timeout-seconds: 900
    poll-seconds: 5
    bindings:
      - namespace: default
        connection-name: nhn-kr1
        region: kr1
        secret-path: secret/data/csp/nhn
```

Use the actual connection name returned for the cluster. The configured region must match its assigned region. For a non-default credential holder, Tumblebug uses `secret/data/users/<holder>/csp/nhn`; bind that holder's existing path to its exact project/connection. Duplicate bindings are rejected. Read access is required only for the configured NHN secret paths; mount the OpenBao token file read-only. Example OpenBao policy for the default holder:

```hcl
path "secret/data/csp/nhn" {
  capabilities = ["read"]
}
```

The existing KV v2 secret must contain `NHN_IDENTITY_ENDPOINT`, `NHN_USERNAME`, `NHN_PASSWORD`, and `NHN_TENANT_ID`. A credential previously registered only in Spider may not have been stored in OpenBao. Missing or masked values result in `NHN_CREDENTIAL_UNAVAILABLE`; AM does not fall back to another account's secret.

The NHN credential must have NKS permissions on the selected project. The NKS registry must be enabled and the cluster must support managed add-ons. AM queries compatible versions with the native cluster's Kubernetes version, base image UUID and platform version. An existing failed add-on must be resolved in NKS first; AM does not upgrade or replace it.

## AM API

All endpoints require the existing AM authentication and project context. Query parameters are `namespace` and `clusterName`; there are no credential or arbitrary endpoint request fields.

| Method | Path | Result |
|---|---|---|
| GET | `/applications/k8s/nhn-cinder-addon` | Provider support, compatibility or readiness, and whether preparation can start |
| POST | `/applications/k8s/nhn-cinder-addon` | HTTP 202 with a preparation job |
| GET | `/applications/k8s/nhn-cinder-addon/jobs/{id}` | `QUEUED`, `RUNNING`, `READY`, or `FAILED` with an actionable error code |

AM requests `POST /v1/clusters/{native-cluster-uuid}/addons/` with only the Cinder name, a compatible version and `resolve_conflicts: none`. The native cluster and installed add-on project IDs must match the authenticated tenant. Installation jobs are deduplicated per namespace/cluster and have a bounded queue. They contain no passwords or IaaS tokens.

Jobs are local to one AM process and expire two hours after completion. After an AM restart or a lost response, refresh capability and retry preparation: the existing native installation is inspected and reused. A timeout stops verification and does not remove NKS resources. Separate AM replicas do not share job records; NKS conflicts are surfaced rather than retried as overwrites.

## Local verification

```powershell
.\gradlew.bat --offline test --tests '*Nhn*' --tests '*K8sJupyter*' --tests '*JupyterStorage*'
cd applicationFE
npm ci --ignore-scripts
npm run test:nhn-cinder-addon
npm run build
```

Build the executable AM JAR with the frontend included from the repository root:

```powershell
.\gradlew.bat --offline -PincludeFrontend test bootJar
```

The output is `build/libs/am.jar`. See [the local validation results](nhn-cinder-addon-validation.md) for the tested scope and artifact checksum.

`NhnCinderAddonLocalIntegrationTest` uses real loopback HTTP for mocked OpenBao, NHN Identity and NKS, plus Fabric8's Kubernetes mock server. It exercises the AM REST job through compatible version selection, installation, worker registration, idempotent reuse, StorageClass creation and Jupyter storage validation. No request goes to a real NHN account. Unit tests cover non-NHN rejection, project isolation, token/tenant mismatch, cloud failures, disabled configuration, duplicate jobs, readiness timeout and stale frontend responses.

References: [NKS API](https://docs.nhncloud.com/ko/Container/NKS/ko/public-api/) and [NHN IaaS token](https://docs.nhncloud.com/ko/nhncloud/ko/public-api/iaas-token/).
