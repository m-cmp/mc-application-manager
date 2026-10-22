# Catalog package sources

Default catalogs keep their original image Registry, external Helm repository, or bundled chart.
They are not redirected by a global Nexus setting.

Public Registry registration first copies the selected image or chart into Nexus, verifies the
copy, and then links the returned package ID to the new catalog. The package records
`artifactSource=NEXUS` and the actual deployment reference. A missing or invalid imported
reference fails deployment; it does not silently select an external image.

`Existing Package` links an available package while keeping that package's recorded source.
An import never rewrites a seeded catalog with the same package name/version.
If catalog creation fails after import, the imported package remains available for a retry.

## Docker images

`PackageInfo.repositoryUrl` is the full image reference for imported packages. The VM deployment
uses that reference and short-lived Registry authentication. The Docker config is removed after
the pull; the deployment does not save a Docker login on the VM.

The optional settings separate endpoints reachable from AM and from deployment VMs:

```yaml
nexus:
  registry-endpoint: ${NEXUS_REGISTRY_ENDPOINT:}
  deployment-registry-endpoint: ${NEXUS_DEPLOYMENT_REGISTRY_ENDPOINT:}
```

An empty deployment endpoint uses AM's Registry endpoint. Configure the deployment endpoint
when AM uses an internal container hostname that the VMs cannot resolve. Registry credentials
are only prepared for the configured deployment Registry, never an arbitrary image host.

Set `NEXUS_DOCKER_REPOSITORY` or `NEXUS_HELM_REPOSITORY` explicitly if more than one hosted
repository of that format exists. A group/proxy repository is not selected as an import target.

## Helm charts

Public registration succeeds only after the exact chart name and version are verified in Nexus.
The imported chart records the Nexus repository URL and repository name. Chart upload failure
is not converted into an external-repository registration success.

Downloads use `helm pull --repo ... --version ...` (or an OCI chart reference), so the repository
index determines the actual archive URL. Import files live in a separate temporary directory
for each request and are removed on both success and failure.

The chart package and its container images are separate artifacts. Deployments preserve the
image locations specified by the chart; copying the chart does not imply all its images or
subchart images were mirrored. Seeded charts and bundled charts retain their existing behavior.

Imported charts are downloaded with authentication from the configured Nexus repository into
a temporary archive before installation. Credentials are not saved in Helm's global repository
configuration. A different repository URL or redirect is rejected before credentials can be
sent elsewhere; a private Nexus repository does not require anonymous access for deployment.

## Compatibility and schema

`PACKAGE_INFO.artifact_source` and `HELM_CHART.artifact_source` are nullable string columns.
Existing null values retain legacy external/bundled behavior. `DDL_AUTO=update` adds the columns;
installations using schema validation must add them before deploying this version.

Old rows are not marked as imported based only on a name or URL guess. Backfill them only after
verifying the corresponding artifact exists in the intended Registry. The source marker is
read-only in the API; ordinary catalog edits cannot relabel a package as a verified import.

## Embedding behind an IP-based Web Console

On server 52, Web Console is served at `https://52.24.20.63:3001` and AM's API/assets at
`https://52.24.20.63:18084`. A CSP `frame-ancestors` host source containing a public IP is not a
reliable way to allow the other port: [CSP's host matching rules](https://www.w3.org/TR/CSP/#match-hosts)
reject IP host sources. The empty iframe can still produce an HTTP 200 server access-log entry.

The deployed integration uses the Console origin for AM HTML documents:

- Proxy `/web/`, `/catalog-icons/`, and `/images/` on Console's port 3001 to AM, retaining request
  paths and forwarding headers. The document response uses `frame-ancestors 'self'`.
- Set only `mc-application-manager-fe.baseurl` in Web Console API's `api.yaml` to
  `https://52.24.20.63:3001`; retain AM's existing API endpoint.
- Build the frontend with `VITE_ASSET_BASE_URL=https://52.24.20.63:18084/` and
  `VITE_API_URL=https://52.24.20.63:18084`. Asset URLs, including lazy chunks and CSS, then remain
  on AM's existing endpoint. Without an asset override, normal standalone builds still use `/`.

This avoids adding wildcard frame ancestors or disabling browser security. Nginx and Console
API configuration backups and a rollback script are stored with the deployment backup.
IAM-enabled consoles also read the registered `mc-application-manager-fe` service URL from IAM;
update that public frontend URL and `MC_APPLICATION_MANAGER_PUBLIC_HOST` to the same origin.
