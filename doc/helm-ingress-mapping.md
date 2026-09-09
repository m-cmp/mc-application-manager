# AM Helm Ingress mapping

## Scope

`HelmIngressValues` converts the existing AM Ingress fields into typed Helm YAML.
Both the request-driven and catalog-only deployment paths use the same adapter.
`KubernetesDeployService` validates the settings before connecting to the cluster or
installing metrics-server / ingress-nginx. The direct Helm entry points also validate.

The controller implementation is unchanged. This change fixes application chart
values; it does not install a different controller per application.

Supported chart identities and render-tested versions:

- Grafana, `https://grafana.github.io/helm-charts`, chart `grafana` 7.3.0:
  `ingress.hosts`, `ingress.path`, `ingress.ingressClassName`, `ingress.tls[]`.
- Prometheus Community, `https://prometheus-community.github.io/helm-charts`, chart
  `prometheus` 25.8.0: `server.ingress.hosts`, `server.ingress.path`,
  `server.ingress.ingressClassName`, `server.ingress.tls[]`.
- Jacob Colvin, `https://jacobcolvin.com/helm-charts`, chart `rclone` 1.0.1:
  `ingress.main.hosts[].host`, `ingress.main.hosts[].paths[]`,
  `ingress.main.ingressClassName`, `ingress.main.tls[]`.
- Bitnami, `https://charts.bitnami.com/bitnami`, chart `nginx` 21.1.23:
  `ingress.hostname`, `ingress.path`, `ingress.ingressClassName`,
  `ingress.extraTls[]`. `ingress.tls=false` and `selfSigned=false` avoid chart-derived
  Secret names / certificate generation; `extraTls` references the selected Secret.

Repository alias changes and trailing URL slashes are accepted. A chart from a
different repository, including a mirror or a different vendor with the same chart
name, uses the generic fallback unless a matching adapter is registered. Unknown
charts are no longer rejected merely because their identity is not in the list.
The fallback uses the default `helm create` Ingress schema:

```yaml
ingress:
  enabled: true
  className: nginx
  hosts:
    - host: app.example.com
      paths:
        - path: /
          pathType: Prefix
  tls: []
```

When Ingress is disabled, the fallback explicitly sets `ingress.enabled=false`.
When TLS is enabled, its `tls` list contains the selected `secretName` and `hosts`.
This is a default convention, not a Helm-enforced standard: a differently structured
chart can ignore these values or fail to render and still needs its own adapter.
The old AM code also sent common values without knowing every chart, but its
flat host list and `tls.enabled` object were not this helm-create schema.
Chart versions are not restricted by the mapper; new versions must be
regression-tested before use.

## Inputs and limits

- Existing precedence remains request > catalog > default. Disabling Ingress ignores
  stale Host / TLS fields. Disabling TLS emits no TLS route even if the catalog had TLS enabled.
- Enabled Ingress needs a DNS Host, absolute Path, and valid Ingress Class. Routes use
  `Prefix`. Existing Host URL normalization in `DeploymentConfigDTO` is preserved.
- Prometheus 25.8.0 emits its Host without YAML quotes; wildcard Host fails rendering.
  The adapter rejects wildcard Prometheus Hosts with a specific error. Scalar-like
  Host / Class values that affected templates would convert to booleans or numbers
  are also rejected. Other tested charts accept wildcard Hosts.
- TLS retains the original Secret selection: request > catalog > `<actual-release-name>-tls`
  when the effective value is `null`. The fallback is resolved after the actual Helm release
  name is generated, for both request-based and catalog-only entry points. No certificate
  is generated. Explicit invalid names (including blank/whitespace API values) fail early.
  The form serializes its empty Secret input as `null` in both check and deploy requests.
- Spec Check warns if the effective Secret name will be generated later, or if a selected
  Secret is absent, unreadable, or lacks the usual TLS type/keys. These are advisory, not
  deployment blocks: an external certificate manager may provision it later. The Helm
  entry points also log advisory checks using the resolved name in workload namespace
  `default`. Only the named Secret is read; its contents are never returned or logged.
  AM does not verify expiry, certificate/key correspondence, hostname coverage, or HTTPS
  reachability, and does not issue/renew certificates or silently disable TLS.
- Subpath routing does not configure Grafana root URLs, Prometheus external URLs,
  application redirects, or rewrite annotations. `/app` rendering alone does not prove
  that a particular application supports serving its UI there. Prefer separate Hosts
  with `/` unless application subpath settings are also verified.
- This change does not add arbitrary Kubernetes namespace selection. Existing AM
  workload callers still use `KubernetesNamespaces.APPLICATION_WORKLOAD` (`default`);
  CB-Tumblebug `namespace` continues to mean the project. Supporting a selectable
  workload namespace requires a separate API field, persistence, and lifecycle /
  monitoring propagation, including handling pre-existing deployments.
- Sequential Host/Path collision checking is described below. Concurrent installation,
  shared controller ownership and TLS certificate lifecycle are not changed by this check.
- The current frontend TLS checkbox / Secret input remain commented out even though
  their state is sent to the API. To request HTTP explicitly, send
  `ingressTlsEnabled: false`; omitting the field inherits the catalog's value.
- mc-admin-cli installs AM with Docker Compose. Its checked-in Compose service has
  no application-chart Ingress mapping to update. An updated AM image must be built
  and selected in the installation workflow for this code to take effect.

## Reproduce tests

### Sequential route collision check

The installation form's existing **Spec Check** button now calls
`POST /applications/k8s/ingress/check` before its resource-spec check. The request includes
the CB-Tumblebug `namespace`, `clusterName`, `catalogId`, and current Ingress fields
(`ingressEnabled`, `ingressHost`, `ingressPath`, `ingressClass`, `ingressTlsEnabled`,
`ingressTlsSecret`). Project authorization runs before cluster access. The response's
`data` contains `valid`, `errors`, and `warnings`; `valid: false` prevents deployment.
Unlike a CPU/memory shortage, an Ingress conflict cannot be overridden by a confirmation.
The existing `GET /applications/k8s/check` Boolean contract remains unchanged.

Changing the target, catalog, Ingress/TLS fields or project context invalidates the
completed check and clears stale messages. In-flight results for earlier settings are
ignored, including changing a value and then reverting it while the check runs. TLS
warnings are visible in the modal but do not disable Deploy. VM resource checks are
unchanged. Spec Check does not install anything or write deployment history.

`KubernetesDeployService` checks the selected cluster's actual Ingress resources through
`KubernetesIngressRouteValidator` before installing metrics-server / the controller /
the application, or opening access. The outer orchestration can still update status and
record a failed attempt. It lists across all Kubernetes namespaces;
Tumblebug project boundaries do not separate HTTP routes on the same controller.
This final server-side check is retained for API callers and changes after Spec Check;
the UI and deployment use the same route validator, without a reservation or lock.

- The effective Ingress Class is `spec.ingressClassName`, falling back to the legacy
  `kubernetes.io/ingress.class` annotation. A different explicit class is ignored.
  Classless legacy routes are conservatively included because they may be served by the controller.
- Host comparison ignores case. Exact Hosts, one-label wildcard overlaps, and hostless
  catch-all rules reserve the same normalized Path. `/app` and `/app/` count as one route.
- Different Paths remain allowed: `/` and `/grafana`, `/grafana` and `/prometheus`, and
  `/app` and `/app/child` are not duplicate reservations. Path case is preserved.
- AM reserves Host/Path regardless of an existing rule's Path type. It does not provide
  a facility for intentionally reusing the same pair with different Exact/Prefix rules.
- Existing applications are not exempt by chart/catalog name. A new install with an
  existing route fails, even while the old Ingress is terminating. Delete the old route
  and wait for its removal before reusing it. This is not an in-place upgrade endpoint.
- The deployment failure log identifies the conflicting namespace/resource. Failure to list Ingresses
  also stops installation; it is not treated as an empty list. The AM cluster credentials
  need cluster-wide `list` permission for `networking.k8s.io/ingresses`.
- This is a read/check operation for sequential deployments only. No locks, reservations,
  admission webhooks or concurrent-deployment handling are introduced.
- Workload namespace remains the literal `default`. The current AM deployment path does
  not call `ensureNamespaceExists` or pass Helm `--create-namespace`. Kubernetes normally
  creates `default` during cluster initialization; AM does not recreate a missing namespace.
- This is the normal Helm deployment path. The separately implemented `K8sJupyterService`
  retains its existing dedicated-Host duplicate check, requested workload namespace,
  and HTTP-only policy. The new preflight also checks its dedicated-Host restriction.
  This change does not alter that service's namespace or TLS behavior.

### Running tests

Normal unit tests do not download charts or require a cluster:

```sh
bash gradlew test
```

The UI tests execute the production form functions and real Vue watcher with mocked APIs:

```sh
cd applicationFE
npm run test:ingress-preflight
npm run type-check
npm run build-only
```

For real Helm rendering, run from the AM repository root with Helm available in PATH.
The Nginx fixture is the existing repository `nginx/` chart. Download the other exact
chart versions with their bundled dependencies:

```sh
ingress_fixture_dir=$(mktemp -d)
helm pull grafana --repo https://grafana.github.io/helm-charts --version 7.3.0 --untar --untardir "$ingress_fixture_dir"
helm pull prometheus --repo https://prometheus-community.github.io/helm-charts --version 25.8.0 --untar --untardir "$ingress_fixture_dir"
helm pull rclone --repo https://jacobcolvin.com/helm-charts --version 1.0.1 --untar --untardir "$ingress_fixture_dir"
AM_HELM_TEST_CHARTS_DIR="$ingress_fixture_dir" bash gradlew test --rerun-tasks
```

The optional render suite checks 100 combinations: four upstream charts plus an
actual `helm create` chart generated in the test's temporary directory, two Helm namespaces
(`default`, `team-monitoring`), and five modes (disabled, HTTP `/`, HTTP subpath,
TLS with a custom external Secret, TLS with the release-name fallback), each with
and without the CIDR values merge.
It verifies the generated Ingress rules, Class,
Path type, TLS lists and referenced Service / port. Namespaces omitted from manifests
inherit the Helm release namespace; this is distinct from proving live AM namespace
selection. The suite runs `helm template --kube-version 1.30.0`, not server-side
admission, Pod scheduling or an HTTP request through a live controller.

Unit tests additionally cover request/catalog precedence, generic fallback for
unknown/missing repositories and unknown chart names, invalid inputs, YAML string
preservation, independent requests, rejection of invalid input before cluster side
effects, and accepted fallback deployments reaching the mocked cluster boundary.
Real-cluster verification remains necessary for controller
readiness, RBAC, Secret existence, DNS, reachability, redirects and application responses.
