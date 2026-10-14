# Docker Hub image import without a Docker Engine

Public Registry / VM / Import & Create keeps the same UI and API flow. AM copies
the selected Docker Hub image to the existing Nexus Registry with Skopeo, bundled
in the AM image. It does not use a Docker socket, start Docker-in-Docker, open port
2375, edit host daemon settings, restart Docker, or run the imported image.

The existing NEXUS OSS record supplies the REST address, username and password.
The default Registry endpoint is its hostname plus `NEXUS_DOCKER_PORT` (5000).
In mc-admin-cli's normal deployment, AM and Nexus share a Docker network, so the
internal Nexus service hostname and port work without installer changes. Only
deploying the new AM image is required. Existing Registry authentication, write
permission, license acceptance and connectivity remain prerequisites.

An optional `NEXUS_DOCKER_REGISTRY_URL` overrides the Registry host, port and
HTTP/HTTPS scheme when REST and image traffic use different endpoints. It is not
a new UI field and is unnecessary in the normal internal-network configuration.
HTTP is allowed only for the explicitly selected destination; source Docker Hub
always uses verified HTTPS. HTTP remains unencrypted. HTTPS destinations retain
certificate verification and need a trusted certificate.

The copy preserves source digests and the complete multi-platform image. This
can transfer more data than the previous single-platform Docker pull/push.
Credentials use a per-operation, mode-600 auth file in a mode-700 directory, not
command arguments. Files are removed after success, failure, timeout or interruption.
The existing copy timeout/retry settings apply. Failed transfer stops metadata
registration and displays the import error instead of claiming success.

VM software deployment and its SSH-based Docker use are unchanged. The source
path currently supports the same public Docker Hub search flow; private-registry
authentication is not newly added by this change.

Tests TC-APP-009/010 keep their UI steps and expected Nexus image/tag results.
Their prerequisite is Registry connectivity/authentication/write permission,
not an AM-to-Docker-Engine connection. VM Terminal Docker checks remain unchanged.

Official reference: https://github.com/podman-container-tools/skopeo/blob/main/docs/skopeo-copy.1.md
