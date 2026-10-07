# Kubernetes Jupyter through the existing Ingress

The existing Jupyter Docker catalog is also selectable for K8s. No catalog SQL
seed change is required. The same image version, Jupyter UI and
`notebooks/object-storage.ipynb` are used. Kubernetes receives a Secret, ConfigMap,
10 GiB PVC, single-replica Deployment, ClusterIP Service (8888) and dedicated-host
Ingress. These resources use `mcmp-jupyter-<deployment ID>` names and ownership
labels; they are not a Helm release. Other catalog applications retain their Helm
deployment path.

New Jupyter installations use the Kubernetes `default` namespace, matching the
ordinary Helm application policy. The selected AM/Tumblebug project (for example,
`my-project`) still scopes cluster lookup, deployment history and Object Storage
grants; a Kubernetes namespace named after that project is not required. Status
monitoring, SSH tunnel recovery and application lifecycle operations also use the
workload namespace. Older Jupyter installations in a project-named namespace
remain managed at their original location; notebooks are not moved or deleted.

## Before installing

New installations use SSH transport through Kubernetes port-forward by default.
Build/publish the sidecar and set `OBJECT_STORAGE_K8S_SSH_IMAGE` as described in
[k8s-jupyter-ssh.md](k8s-jupyter-ssh.md). The external Gateway URL/HTTP configuration
below applies only when explicitly selecting `OBJECT_STORAGE_K8S_TRANSPORT=DIRECT`.
Both modes preserve the common application CIDR policy in [k8s-cidr.md](k8s-cidr.md).

- The Kubernetes `default` namespace must already exist. Select a StorageClass in
  the existing installation form. The Pod must be able to pull the catalog image.
- For DIRECT transport, set `OBJECT_STORAGE_K8S_GATEWAY_URL` on AM to the **full** gateway URL reachable
  from Pods, for example `https://am.example.com/applications/object-storage-gateway`.
  VM `127.0.0.1:18084` SSH transport is not used in Kubernetes. Internal HTTP is
  accepted by default only for `.svc` / `.svc.cluster.local` addresses. External HTTPS must
  have a certificate trusted by Python requests in the Jupyter image. For a private
  CA, prepare an image with the CA in its trust bundle. No TLS bypass is installed.
- Pods need outbound access to AM and CSP Object Storage HTTPS endpoints. Setting
  the URL does not create a private network, VPN or reverse tunnel.

For HTTP testing against the development AM, set both environment variables on
the AM backend and restart it:

```text
OBJECT_STORAGE_K8S_ALLOW_HTTP=true
OBJECT_STORAGE_K8S_GATEWAY_URL=http://210.217.178.130:18084/applications/object-storage-gateway
```

Use that address only when it reaches this AM instance from the target Pod.
HTTP carries the grant token without encryption; this opt-in defaults to false.
Loopback, URL credentials and query strings are still rejected. VM SSH tunnel
success does not prove Pod-to-AM connectivity. Verify routing, AM's published
port, firewall and Pod egress; startup checks authenticated gateway access and
object listing before Jupyter becomes ready. The user's Ingress CIDR on port
30880 is unrelated to this outbound connection to AM on port 18084.

- Retain the existing ingress-nginx NodePort 30880 and class `nginx`. New AM-created
  controllers use `externalTrafficPolicy: Local`; older controllers must have that
  setting applied in their Helm values before installation. AM validates it and
  does not silently reconfigure a shared controller. This preserves client source
  IPs for CIDR matching. Do not enable untrusted forwarded-header processing.
- With `Local`, the public IP used for access must belong to a node with a ready
  Ingress Controller endpoint. A random worker without an endpoint will not work.
- For automatic firewall changes, Tumblebug must report exactly one registered
  worker SG in `cluster.network.securityGroupIds`. Verify this is attached to the
  chosen ingress node(s). AM refuses missing/ambiguous groups and does not register
  arbitrary CSP SGs or change cluster node membership. Without automatic firewall
  changes, an administrator must allow the user CIDR on TCP 30880 beforehand.

## Installation and access

Select the existing Jupyter catalog, enable Ingress, enter a unique DNS hostname,
path `/`, and a restricted IPv4 CIDR. Select registered Object Storages, their
Prefix / READ_ONLY or READ_WRITE mode and the Jupyter login token as for VM installs.
The storage check confirms Tumblebug access; installation separately checks AM
authentication and object listing **inside the Pod** before starting Jupyter.

For a test PC, add `NODE_PUBLIC_IP jupyter-test.example.com` to its hosts file,
then open `http://jupyter-test.example.com:30880/`. Use the Ingress hostname, not the
VM-only Jupyter address. NodePort 30880 is shared by application hostnames. The
per-Jupyter Ingress CIDR restriction remains separate from the shared SG rule.
Changing the input CIDR does not retroactively restrict other existing Helm applications.
New Helm installations follow the common application CIDR policy.

Ingress is created only after the Deployment is ready and optional SG provisioning
succeeds. Workload readiness can wait up to
`OBJECT_STORAGE_K8S_READY_TIMEOUT_SECONDS` (default 1800); long image pulls can
exceed a frontend proxy timeout, so inspect AM and Pod status before retrying.

## Lifecycle and data

- Start/stop/restart scale or restart only the owned Deployment. Jupyter remains
  single-user, one replica; HPA and workload rebalancing are rejected.
- Grants are scoped to deployment ID plus `k8s:<cluster ID>`, without VM SSH keys.
  Grant token and Jupyter login token are stored in a Kubernetes Secret. They are
  never placed in ConfigMaps or Ingress annotations.
- Failed installation revokes the grant and attempts resource/firewall cleanup.
  Incomplete cleanup is tracked as DELETE_PENDING and can be retried via uninstall.
- Uninstall revokes access and deletes owned compute/configuration/route resources.
  Shared SG rules remain until their last AM dependency is removed; operator-owned
  rules are preserved by the existing exposure ledger.
- PVCs are retained on uninstall and failed startup. Their names include the old
  deployment ID. Reinstallation creates a new PVC; restore/copy the retained data
  explicitly if needed. No notebook data is automatically deleted or overwritten.

## Validation

Run backend tests, frontend type-check/build, and
`node scripts/test-k8s-jupyter.mjs`. Build the frontend into `src/main/resources/static`
before packaging an image, as for the existing AM release workflow.

Live acceptance on each CSP still requires: allowed client receives the Jupyter
login page; a client outside the CIDR is rejected; spoofed X-Forwarded-For cannot
bypass the restriction; Pod restart retains the notebook; read-only upload is
rejected; read/write upload and download succeed; shared SG rules survive deleting
another application. Automated local tests do not provision CSP resources.
