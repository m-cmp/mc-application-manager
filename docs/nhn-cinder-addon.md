# NHN managed Cinder CSI installation

AM can install `cinder-csi-plugin` directly through NHN NKS for an NHN Kubernetes cluster. Tumblebug continues to locate the selected cluster and its cloud connection. By default AM reads the selected cluster's existing `kube-system/os-trustee` Secret, authenticates its delegated OpenStack trust over the fixed official NHN HTTPS Identity v3 API, and uses the public NKS endpoint for the Tumblebug connection's region. AM does not need an OpenBao token, AppRole, stored credential binding or an AM encryption key for this path.

The installation form's NHN storage setup now offers **Install Cinder CSI add-on**. It waits for the NKS installation to complete and for `cinder.csi.openstack.org` to be registered on all CSI nodes. The user can then create the existing NHN StorageClass or select an existing one and deploy Jupyter. An existing StorageClass does not bypass the CSI readiness check. A working manual or managed CSI installation is reused without reading cloud credentials or changing add-ons.

## Configuration

The feature is enabled by default in `src/main/resources/application.yaml` through `app.nhn-cinder-addon.enabled: ${NHN_CINDER_ADDON_ENABLED:true}`. Set the YAML value to `false`, or set the environment variable `NHN_CINDER_ADDON_ENABLED=false`, to disable managed add-on installation. Restart AM after changing the configuration. A disabled feature does not read cloud credentials or call NKS to install an add-on; existing CSI readiness and StorageClass use remain available.

The common `application.yaml` selects `credential-source: cluster` by default. The same setting works in the local profile. A new AM deployment needs the normal AM/Tumblebug connection and the registered NKS kubeconfig with permission to read `kube-system/os-trustee`; it does not need an AM-specific OpenBao initialization step or an mc-admin-cli change.

```yaml
app:
  nhn-cinder-addon:
    enabled: true # false disables managed add-on installation; restart AM after a change
    credential-source: cluster
    ready-timeout-seconds: 900
    poll-seconds: 5
```

The fixed Secret supplies only `os-trusteeID`, `os-trusteePassword` and `os-trustID`. Its internal `os-authURL` and `os-region` are not used as request destinations or as public region identifiers. The verified NKS cluster has an internal HTTP identity broker and `RegionOne`, while its public NKS region is `kr1`. AM always authenticates at `https://api-identity-infrastructure.nhncloudservice.com/v3/auth/tokens`, verifies the returned trust ID, expiry and regional public service catalog, and checks that the native cluster UUID and project ID match before installation. Trust passwords and tokens stay in memory and are not returned to the browser, logged or stored in AM DB/files.

Each operation obtains a fresh token from the current Secret. During installation polling AM reauthenticates before token expiry and rechecks the selected native cluster/project without repeating an installation POST. AM restart or container recreation therefore does not require preserving an AM token file. A missing/revoked cluster trust, inadequate Kubernetes RBAC, disabled NKS registry or unsupported add-on still requires correcting the actual NKS configuration; AM does not substitute another account's credential.

### Optional legacy OpenBao mode

`credential-source: openbao` explicitly enables the previous configuration. Only this mode reads the existing OpenBao settings and project/connection bindings in `application.yaml` or `application-local.yaml`; those settings are ignored in cluster mode. There is no automatic fallback to OpenBao.

```yaml
app:
  nhn-cinder-addon:
    enabled: true
    credential-source: openbao
    open-bao-url: http://openbao:8200
    open-bao-token-file: /run/secrets/am-nhn-openbao-token
    bindings:
      - namespace: default
        connection-name: nhn-kr1
        region: kr1
        secret-path: secret/data/csp/nhn
```

Legacy mode requires a read-only OpenBao token for the configured KV v2 path. The secret contains `NHN_IDENTITY_ENDPOINT`, `NHN_USERNAME`, `NHN_PASSWORD` and `NHN_TENANT_ID`. Use the actual project namespace, connection name and public region; duplicate bindings are rejected. Non-default credential holders can bind `secret/data/users/<holder>/csp/nhn`. Token values must not be added to YAML or frontend requests.

The authenticated NHN trust/account must have NKS permissions on the selected project. The NKS registry must be enabled and the cluster must support managed add-ons. AM queries compatible versions with the native cluster's Kubernetes version, base image UUID and platform version. An existing failed add-on must be resolved in NKS first; AM does not upgrade or replace it.

## AM API

All endpoints require the existing AM authentication and project context. Query parameters are `namespace` and `clusterName`; there are no credential or arbitrary endpoint request fields.

| Method | Path | Result |
|---|---|---|
| GET | `/applications/k8s/nhn-cinder-addon` | Provider support, compatibility or readiness, and whether preparation can start |
| POST | `/applications/k8s/nhn-cinder-addon` | HTTP 202 with a preparation job |
| GET | `/applications/k8s/nhn-cinder-addon/jobs/{id}` | `QUEUED`, `RUNNING`, `READY`, or `FAILED` with an actionable error code |

AM requests `POST /v1/clusters/{native-cluster-uuid}/addons` with the catalog name `cinder_csi_plugin`, a compatible version and `resolve_conflicts: none`. The cluster add-on route uses no trailing slash: a live KR1 check returned 404 for the slash form and 200 for the canonical form. Both `cinder_csi_plugin` catalog entries and `cinder-csi-plugin` cluster entries are recognized as Cinder. `NOT_INSTALLED` placeholder entries are treated as absent after project/cluster validation, so AM requests an installation rather than waiting for a nonexistent one. The native cluster and installed add-on project IDs must match the authenticated tenant. Installation jobs are deduplicated per namespace/cluster and have a bounded queue. They contain no passwords or IaaS tokens.

Jobs are local to one AM process and expire two hours after completion. After an AM restart or a lost response, refresh capability and retry preparation: the existing native installation is inspected and reused. A timeout stops verification and does not remove NKS resources. Separate AM replicas do not share job records; NKS conflicts are surfaced rather than retried as overwrites.

## Local configuration

Start the backend with `--spring.profiles.active=local`. Cluster authentication remains the default; the legacy local OpenBao URL/token-file values are unused unless `NHN_CINDER_ADDON_CREDENTIAL_SOURCE=openbao` is set. `NHN_CINDER_ADDON_ENABLED=false` disables installation in either mode.

The installation button appears when capability returns `canInstall: true`. `NOT_CONFIGURED` identifies a disabled feature, invalid authentication-source setting or unavailable selected region (and, in legacy mode, a missing binding). `NHN_CLUSTER_IDENTITY_UNAVAILABLE` means the selected cluster's fixed trust Secret could not be read. Refresh StorageClasses after restarting the backend to reload capability.

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

`NhnCinderAddonLocalIntegrationTest` exercises both cluster trust authentication with no OpenBao configuration and explicit legacy OpenBao authentication. It uses real loopback HTTP for mocked Identity/NKS (and legacy OpenBao), plus Fabric8's Kubernetes mock server. It exercises the AM REST job through compatible version selection, installation, worker registration, idempotent reuse, StorageClass creation and Jupyter storage validation. No request goes to a real NHN account. Unit tests cover non-NHN rejection, project isolation, token/tenant mismatch, cloud failures, disabled configuration, duplicate jobs, readiness timeout and stale frontend responses.

References: [OpenStack Identity v3 trust scope](https://docs.openstack.org/openapi/latest/identity.html) and [NKS API](https://docs.nhncloud.com/ko/Container/NKS/ko/public-api/) and [NHN IaaS token](https://docs.nhncloud.com/ko/nhncloud/ko/public-api/iaas-token/).
