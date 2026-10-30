package kr.co.mcmp.softwarecatalog.docker.service;

import java.util.Locale;

/** Fixed host-side checks; never interpolate user-provided commands into these scripts. */
final class VmDeploymentChecks {
    // Bootstrap minimum for supported Elasticsearch releases, not the higher performance recommendation.
    static final int ELASTICSEARCH_MIN_MAP_COUNT = 262144;

    private VmDeploymentChecks() { }

    static boolean isElasticsearch(String image) {
        return image.toLowerCase(Locale.ROOT).contains("elasticsearch");
    }

    static String elasticsearchPrerequisites() {
        return """
                mcmp_current=$(cat /proc/sys/vm/max_map_count) || exit 1
                case "$mcmp_current" in ''|*[!0-9]*) echo 'Cannot read vm.max_map_count' >&2; exit 1;; esac
                if [ "$mcmp_current" -ge 262144 ]; then
                  printf 'vm.max_map_count=%s; minimum=262144; unchanged\n' "$mcmp_current"
                  exit 0
                fi
                if ! sudo -n true 2>/dev/null; then
                  echo "Elasticsearch prerequisite failed: vm.max_map_count=$mcmp_current; minimum=262144. Passwordless sudo is required to increase it." >&2
                  exit 77
                fi
                sudo -n sh <<'MCMP_ELASTICSEARCH_SYSCTL'
                  set -eu
                  config=/etc/sysctl.d/99-mcmp-elasticsearch.conf
                  current=$(cat /proc/sys/vm/max_map_count)
                  if [ "$current" -ge 262144 ]; then echo "vm.max_map_count=$current; unchanged"; exit 0; fi
                  if [ -L "$config" ] || { [ -e "$config" ] && ! grep -qx "# Managed by MC-Application Manager" "$config"; }; then
                    echo "Elasticsearch prerequisite failed: vm.max_map_count=$current; minimum=262144. Refusing to overwrite $config; ask the administrator to configure it." >&2
                    exit 1
                  fi
                  target=262144
                  if [ -f "$config" ]; then
                    configured=$(sed -n "s/^vm.max_map_count=\\([0-9][0-9]*\\)$/\\1/p" "$config")
                    case "$configured" in ""|*[!0-9]*) echo "Invalid managed sysctl configuration: $config" >&2; exit 1;; esac
                    if [ "$configured" -gt "$target" ]; then target=$configured; fi
                  fi
                  temporary=$(mktemp /etc/sysctl.d/.mcmp-elasticsearch.XXXXXX)
                  trap 'rm -f -- "$temporary"' EXIT HUP INT TERM
                  printf "# Managed by MC-Application Manager\\nvm.max_map_count=%s\\n" "$target" > "$temporary"
                  chmod 644 "$temporary"
                  if ! sysctl -w "vm.max_map_count=$target"; then
                    echo "Elasticsearch prerequisite failed: current=$current; minimum=262144; kernel setting could not be changed." >&2
                    exit 1
                  fi
                  if ! mv -f -- "$temporary" "$config"; then
                    sysctl -w "vm.max_map_count=$current" >/dev/null || true
                    echo "Elasticsearch prerequisite failed: cannot persist $config; attempted to restore $current." >&2
                    exit 1
                  fi
                  actual=$(cat /proc/sys/vm/max_map_count)
                  if [ "$actual" -lt "$target" ]; then echo "vm.max_map_count verification failed: actual=$actual; required=$target" >&2; exit 1; fi
                  echo "vm.max_map_count=$actual; previous=$current; minimum=262144; persisted=$config"
                MCMP_ELASTICSEARCH_SYSCTL
                """;
    }

    static String containerStartup(String createCommand, String httpProbe, int timeoutSeconds) {
        return containerStartup(createCommand, httpProbe, timeoutSeconds, false);
    }

    static String containerStartup(String createCommand, String httpProbe, int timeoutSeconds, boolean deferHealth) {
        return "mcmp_container_id=$(" + createCommand + ") || exit $?\n"
                + "printf '__MCMP_CONTAINER_ID__=%s\\n' \"$mcmp_container_id\"\n"
                + "docker start \"$mcmp_container_id\" >/dev/null || exit $?\n"
                + "mcmp_deadline=$(( $(date +%s) + " + timeoutSeconds + " )); mcmp_stable=0\n"
                + "while [ \"$(date +%s)\" -lt \"$mcmp_deadline\" ]; do\n"
                + "  mcmp_state=$(docker inspect --format '{{.State.Status}}' \"$mcmp_container_id\") || exit $?\n"
                + (deferHealth ? "  mcmp_health=none\n"
                        : "  mcmp_health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \"$mcmp_container_id\") || exit $?\n")
                + "  mcmp_restarts=$(docker inspect --format '{{.RestartCount}}' \"$mcmp_container_id\") || exit $?\n"
                + "  if [ \"$mcmp_state\" = exited ] || [ \"$mcmp_state\" = dead ] || [ \"$mcmp_restarts\" -gt 0 ]; then\n"
                + "    echo \"Container failed during startup: state=$mcmp_state; restarts=$mcmp_restarts\" >&2\n"
                + "    docker logs --tail 30 \"$mcmp_container_id\" >&2; exit 1\n"
                + "  fi\n"
                + "  if [ \"$mcmp_state\" = running ] && { [ \"$mcmp_health\" = healthy ] || [ \"$mcmp_health\" = none ]; }; then\n"
                + (httpProbe == null ? "    mcmp_stable=$((mcmp_stable + 1))\n"
                        : "    if " + httpProbe + "; then mcmp_stable=$((mcmp_stable + 1)); else mcmp_stable=0; fi\n")
                + "    if [ \"$mcmp_stable\" -ge 5 ]; then echo 'Container startup verified'; exit 0; fi\n"
                + "  else mcmp_stable=0; fi\n"
                + "  sleep 1\n"
                + "done\n"
                + "echo \"Container readiness timed out after " + timeoutSeconds + " seconds: state=$mcmp_state; health=$mcmp_health\" >&2\n"
                + "docker logs --tail 30 \"$mcmp_container_id\" >&2\nexit 124";
    }
}
