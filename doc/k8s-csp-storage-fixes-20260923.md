# Kubernetes storage/spec compatibility verification — 2026-09-23

> Follow-up, 2026-09-28: the 52-server was reinstalled. NHN and Tencent live
> retests are complete and owned test resources have been cleaned up. Each passed
> the five-app functional checks; Tencent has a separately documented same-Pod
> Service access limitation. See [the follow-up report](k8s-nhn-tencent-retest-20260928.md).
> The blocked/outstanding entries below are the historical September 23 state,
> not the current closeout status. The old NHN cluster was also confirmed absent
> from the current account before creating the new isolated test cluster.

> Final source adjustment, 2026-09-28: per the user's request, the bundled chart
> version stays at `0.1.0` and the proposed startup version migration was removed.
> The `0.1.1` references and JAR hash below describe the historical test build,
> not the final version policy. NHN storage and Alibaba memory fixes are retained.

## Changes

- Bundled Redis, MariaDB and PostgreSQL validate Cinder CSI registration before
  deployment. For Cinder volumes only, a bounded init container prepares the PVC
  mount root with the application's UID/GID. It does not recursively rewrite data;
  the application remains non-root. Apache/Tomcat and other storage drivers do not
  receive this initializer. Restricted Pod Security can reject the root initializer;
  AM does not relax cluster admission policy.
- The existing NHN StorageClass creation API now explicitly requests `ext4`.
  `Retain` and `WaitForFirstConsumer` remain unchanged. Existing classes and their
  default designation are never modified. The managed Cinder add-on remains a
  cluster prerequisite, not an automatic shared-cluster mutation by AM.
- Spider's positive finite `MemSizeMib` is preferred over legacy memory metadata.
  Missing/invalid normalized fields still use the existing older-response paths.
  This fixes Alibaba's `MemSizeMib=16384` / provider `Memory=0` combination without
  bypassing the capacity comparison or changing the existing continue-anyway policy.
- Bundled chart version is `0.1.1`. Startup migrates only exact bundled `0.1.0`
  identities. Custom mappings, running releases and VM settings are preserved.
- No IBM driver, IBM cluster or production AM image changes were made.

## Local verification

- Full Gradle suite: 688 tests, 0 failures/errors, 1 conditionally skipped test.
- Helm lint/template and AM values-pipeline tests executed with Helm installed.
- Frontend type check/build passed. Built-in form checks passed 44 scenarios,
  including VM/K8s and both old/new chart versions.
- Additional cases cover missing/registering CSI, non-Cinder/stateless workloads,
  bounded init security settings, new ext4 class creation, catalog migration,
  older memory response shapes, invalid normalized capacity and requirements
  both below and above the actual 16GiB capacity.

## Alibaba live verification

Executed from an isolated AM/DB on `52.24.20.63`, not its production AM, through
Spec Check → deployment submission → actual Service access → uninstall.

- Final result: Apache HTTP Server, Tomcat, Redis, MariaDB and PostgreSQL passed.
- HTTP apps returned 200. DB/cache apps accepted valid credentials, rejected
  invalid authentication, and preserved written data after Pod replacement.
- AM logged `16384.0MiB` and available `16.0 GiB`; no test bypassed Spec Check.
- Worker: one `ecs.u1-c1m4.xlarge`, Kubernetes `1.35.7-aliyun.1`, Tokyo.
- Persistent tests used `alicloud-disk-topology-alltype`, 20Gi, matching the AM
  form's existing recommendation and initial size.
- Initial harness selection of `alicloud-disk-efficiency` was incompatible with
  that worker. Its Pod reported unsupported `cloud_efficiency`, so the test was
  stopped, its resources removed, and the harness aligned with the existing UI.
  The initial non-pass is retained in evidence, not counted as a successful run.
- AM can report submission success before actual Pod readiness; independent
  readiness and functional checks were therefore required. That pre-existing
  status behavior was not changed by this scoped compatibility patch.
- Native inventory verified cluster, nodes, disks, SG, SSH key, VPC/subnets,
  managed LB, NAT and test EIPs removed. Protected metadata/container identities
  remained unchanged.

## NHN live verification

Implementation and local tests are complete, but the live test is blocked and
must not be described as passed or cleaned up.

- Created only a new test cluster in `kr1`, one `m2.c4m8` worker, Kubernetes
  `v1.34.3`. The worker was confirmed ACTIVE through the NHN native API.
- At 06:14 UTC, the cluster was still `CREATE_IN_PROGRESS`; the API reported no
  failure reason. No managed Cinder installation, app deployment or test PVC had
  yet been submitted to this cluster.
- At approximately 06:15 UTC (15:15 KST), the 52-server SSH connection was closed
  remotely. Subsequent SSH port 22 and web-console port 3001 requests timed out.
  Connectivity to server 210 and an unrelated public HTTPS endpoint still worked.
  This establishes a 52-server access interruption, not its underlying cause.
- AWS console access was not available as an authenticated session. The AM/server
  was not restarted by this task. Server access recovery is required to finish
  validation and exact-resource cleanup. The user was informed that resources remain.

Outstanding owned NHN resource identifiers (never substitute broad deletion):

- Tumblebug namespace `amfix-0923`, UID `e846394d317346289e9a`.
- Cluster `am5-nhn`, UID/name `tbmpo9fbveq4jn086k0n`, native ID
  `d7870e40-4a07-491d-94da-b1ca132a1587`.
- Worker `9a9ba71c-9691-49a4-9d95-a56e2f81ca25` (belongs to the new cluster).
- VPC `0c6a7af2-076c-4b43-abfb-0cfc4c9eb76b`.
- Subnets `8337179d-ce9c-407c-8887-990c214b12c2`,
  `c579a81f-3313-4574-82e6-bfe0c65c012f`.
- Security group `85dacba7-843b-47f3-9113-d6085849988d`.
- SSH key `tbd8tncetn7n6f8a1dt5`.

The isolated test AM/DB, test network/volume and namespace also require closeout.
On access recovery, first inspect whether the old waiter is still running; do not
blindly rerun initialization or create a second cluster. The evidence directory
contains `nhn.json`, protected baselines and exact-ID guarded cleanup scripts.

## Tencent / server 210

The recent 52-server failure is different from the September 16 test: its default
worker image was Ubuntu 16.04.1, and kube-proxy expected xtables under
`/host/usr/sbin` while the worker supplied them under `/sbin`. No app was deployed.
The September 16 run corrected an empty tag and availability-zone subnet selection,
then succeeded with explicitly requested Ubuntu 22.04 workers. See
[the historical report](nginx-multi-csp-verification-20260916.md).

Read-only SSH checks on `210.217.178.130` found Tumblebug/Spider `0.13.2` and an
active etcd `NOSPACE` alarm. `endpoint health` failed to commit a proposal because
of that alarm. No Tencent cloud resources were created there; no shared etcd
maintenance was attempted. Resolve/verify etcd health before retrying with an
explicit compatible worker image. This is a test blocker, not a Tencent AM app
failure.

## Evidence and scope

Server evidence directory: `/home/ubuntu/am-storage-fix-20260923/csp-run/` (private).
Test namespace: `amfix-0923`; one newly owned cluster at a time. Existing resources,
including archiving resources and concurrent work, are protected.

Test JAR SHA-256: `a732bf5e6ee43892872fadbc97f6da2bc644f5878fe4466c388e1bb0727c5c69`.
Test image: `am-local:storage-fix-20260923`, digest
`sha256:e88c526ea334d5623e6bb1beae537f5fd887d174001851aa6392424838612b13`.
At the end of the September 23 run, later local additions were tests/documentation
only. The final September 28 chart-version adjustment is described above and is
not represented by this historical JAR hash. Public Ingress/CIDR and browser
interaction are not covered by these internal-Service functional tests. No commit,
push or production redeployment was part of the September 23 run.
