package kr.co.mcmp.softwarecatalog.docker.service;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.co.mcmp.softwarecatalog.docker.model.ContainerHealthInfo;
import kr.co.mcmp.softwarecatalog.docker.model.DockerCommandResult;
import kr.co.mcmp.softwarecatalog.docker.model.DockerTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Collects one non-streaming Docker snapshot over SSH.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContainerStatsCollector {

    private static final String INSPECT_BEGIN = "__MCMP_INSPECT_BEGIN__";
    private static final String INSPECT_END = "__MCMP_INSPECT_END__";
    private static final String STATS_BEGIN = "__MCMP_STATS_BEGIN__";
    private static final String STATS_END = "__MCMP_STATS_END__";
    private static final String PORT_ACCESS = "__MCMP_PORT_ACCESS__=";
    private static final String HOST_CPUS = "__MCMP_HOST_CPUS__=";
    private static final String ALLOWED_CPUS = "__MCMP_ALLOWED_CPUS__=";
    private static final String RUNTIME_CPU_QUOTA = "__MCMP_RUNTIME_CPU_QUOTA__=";
    private static final String RUNTIME_CPU_PROBE_OK = "__MCMP_RUNTIME_CPU_PROBE_OK__=";
    private static final Pattern CONTAINER_ID = Pattern.compile("[a-f0-9]{12,64}");
    private static final Pattern SIZE_VALUE =
            Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)\\s*([kmgtpe]?i?b)$", Pattern.CASE_INSENSITIVE);

    private final DockerSshCommandExecutor commandExecutor;
    private final ObjectMapper objectMapper;

    public ContainerHealthInfo collectContainerStats(DockerTarget target, String containerId) {
        String id = validateContainerId(containerId);
        try {
            String quotedId = DockerSshCommandExecutor.shellQuote(id);
            String script = "printf '" + INSPECT_BEGIN + "\\n'\n"
                    + "docker inspect " + quotedId + " || exit $?\n"
                    + "printf '" + INSPECT_END + "\\n'\n"
                    + cpuCapacityScript(quotedId)
                    + "printf '" + STATS_BEGIN + "\\n'\n"
                    + "if [ \"$(docker inspect --format '{{.State.Running}}' " + quotedId
                    + " 2>/dev/null)\" = true ]; then "
                    + "docker stats --no-stream --format '{{json .}}' " + quotedId
                    + " || printf '{}\\n'; else printf '{}\\n'; fi\n"
                    + "printf '" + STATS_END + "\\n'\n"
                    + "mcmp_port=$(docker port " + quotedId
                    + " 2>/dev/null | head -n 1 | awk -F: '{print $NF}')\n"
                    + "mcmp_access=false\n"
                    + "case \"$mcmp_port\" in ''|*[!0-9]*) ;; *) "
                    + "if command -v timeout >/dev/null 2>&1 "
                    + "&& command -v bash >/dev/null 2>&1 "
                    + "&& timeout 2 bash -c \": >/dev/tcp/127.0.0.1/$mcmp_port\" 2>/dev/null; "
                    + "then mcmp_access=true; fi ;; esac\n"
                    + "printf '" + PORT_ACCESS + "%s\\n' \"$mcmp_access\"";

            DockerCommandResult result = commandExecutor.execute(target, script);
            JsonNode inspect = parseInspect(extractSection(result.stdout(), INSPECT_BEGIN, INSPECT_END));
            JsonNode stats = parseStats(extractSection(result.stdout(), STATS_BEGIN, STATS_END));

            String state = textOrNull(inspect.path("State").path("Status"));
            boolean running = inspect.path("State").path("Running").asBoolean(false);
            String healthState = textOrNull(inspect.path("State").path("Health").path("Status"));
            Integer servicePort = extractServicePort(inspect);
            boolean portAccessible = extractMarkerValue(result.stdout(), PORT_ACCESS);

            MemoryValues memory = parseMemoryUsage(textOrNull(stats.path("MemUsage")));
            NetworkValues network = parseNetworkIo(textOrNull(stats.path("NetIO")));
            Double rawCpu = parsePercentage(textOrNull(stats.path("CPUPerc")));
            String allowedCpuList = markerValues(result.stdout(), ALLOWED_CPUS).stream()
                    .findFirst().orElse(null);
            boolean runtimeCpuProbeOk = extractMarkerValue(result.stdout(), RUNTIME_CPU_PROBE_OK)
                    && allowedCpuList != null && !allowedCpuList.isBlank();
            Double cpuCapacity = runtimeCpuProbeOk
                    ? DockerCpuCapacity.resolve(inspect.path("HostConfig"),
                            markerValues(result.stdout(), HOST_CPUS).stream().findFirst().orElse(null),
                            allowedCpuList, markerValues(result.stdout(), RUNTIME_CPU_QUOTA))
                    : null;
            Double cpuUsage = DockerCpuCapacity.normalize(rawCpu, cpuCapacity);

            return ContainerHealthInfo.builder()
                    .status(mapContainerStatus(state))
                    .servicePorts(servicePort)
                    .isPortAccess(servicePort != null && portAccessible)
                    .isHealthCheck(healthState == null ? running : "healthy".equalsIgnoreCase(healthState))
                    .cpuUsage(cpuUsage)
                    .rawCpuUsage(rawCpu)
                    .cpuCapacityCores(cpuCapacity)
                    .cpuMetricsVersion(cpuUsage == null ? null : DockerCpuCapacity.METRICS_VERSION)
                    .memoryUsage(parsePercentage(textOrNull(stats.path("MemPerc"))))
                    .memoryBytes(memory.usedBytes())
                    .memoryLimitBytes(memory.limitBytes())
                    .networkIn(network.inputBytes())
                    .networkOut(network.outputBytes())
                    .oomKilled(inspect.path("State").path("OOMKilled").asBoolean(false))
                    .restartCount(inspect.path("RestartCount").asInt(0))
                    .build();
        } catch (Exception e) {
            log.error("Error collecting container stats through SSH for VM {}, container {}",
                    target.vmId(), id, e);
            return ContainerHealthInfo.builder().status("ERROR").build();
        }
    }

    /** Read-only, shell-only probes; no image shell or cgroup writes are required. */
    private String cpuCapacityScript(String quotedId) {
        return "printf '" + HOST_CPUS + "%s\\n' \"$(docker info --format '{{.NCPU}}' 2>/dev/null)\"\n"
                + "mcmp_cpu_probe_ok=false\n"
                + "mcmp_pid=$(docker inspect --format '{{.State.Pid}}' " + quotedId + " 2>/dev/null)\n"
                + "case \"$mcmp_pid\" in ''|0|*[!0-9]*) ;; *)\n"
                + "mcmp_allowed=$(awk '/^Cpus_allowed_list:/ {print $2; exit}' /proc/$mcmp_pid/status 2>/dev/null)\n"
                + "if [ -n \"$mcmp_allowed\" ]; then printf '" + ALLOWED_CPUS + "%s\\n' \"$mcmp_allowed\"; fi\n"
                + "mcmp_cpu_rel=$(awk -F: '$1 == \"0\" && $2 == \"\" {print $3; exit}' /proc/$mcmp_pid/cgroup 2>/dev/null)\n"
                + "mcmp_cpu_root=/sys/fs/cgroup\n"
                + "mcmp_cpu_v2=true\n"
                + "if [ -z \"$mcmp_cpu_rel\" ]; then\n"
                + "  mcmp_cpu_v2=false\n"
                + "  mcmp_cpu_rel=$(awk -F: '$2 ~ /(^|,)cpu(,|$)/ {print $3; exit}' /proc/$mcmp_pid/cgroup 2>/dev/null)\n"
                + "  for mcmp_candidate in /sys/fs/cgroup/cpu /sys/fs/cgroup/cpu,cpuacct /sys/fs/cgroup/cpuacct,cpu; do\n"
                + "    if [ -d \"$mcmp_candidate\" ]; then mcmp_cpu_root=$mcmp_candidate; break; fi\n"
                + "  done\n"
                + "fi\n"
                + "case \"$mcmp_cpu_rel\" in ''|*..*) ;; /*)\n"
                + "  if [ -n \"$mcmp_allowed\" ] && [ -r /proc/$mcmp_pid/status ] && [ -r /proc/$mcmp_pid/cgroup ]; then\n"
                + "    mcmp_cpu_probe_ok=true\n"
                + "  fi\n"
                + "  if [ ! -d \"$mcmp_cpu_root\" ] || [ ! -x \"$mcmp_cpu_root\" ]; then mcmp_cpu_probe_ok=false; fi\n"
                + "  if [ \"$mcmp_cpu_v2\" = true ] && [ ! -r \"$mcmp_cpu_root/cgroup.controllers\" ]; then mcmp_cpu_probe_ok=false; fi\n"
                + "  mcmp_cpu_path=$mcmp_cpu_root$mcmp_cpu_rel\n"
                + "  mcmp_cpu_path=${mcmp_cpu_path%/}\n"
                + "  while :; do\n"
                + "    if [ \"$mcmp_cpu_v2\" = true ]; then\n"
                + "      if [ \"$mcmp_cpu_path\" = \"$mcmp_cpu_root\" ] && [ ! -e \"$mcmp_cpu_path/cpu.max\" ]; then\n"
                + "        : # cgroup v2 root has no cpu.max; it cannot carry a CPU quota.\n"
                + "      elif [ -r \"$mcmp_cpu_path/cpu.max\" ] && mcmp_quota=$(cat \"$mcmp_cpu_path/cpu.max\" 2>/dev/null); then\n"
                + "        printf '" + RUNTIME_CPU_QUOTA + "%s\\n' \"$mcmp_quota\"\n"
                + "      else mcmp_cpu_probe_ok=false; fi\n"
                + "    elif [ -r \"$mcmp_cpu_path/cpu.cfs_quota_us\" ] && [ -r \"$mcmp_cpu_path/cpu.cfs_period_us\" ] &&\n"
                + "      mcmp_quota=$(cat \"$mcmp_cpu_path/cpu.cfs_quota_us\" 2>/dev/null) &&\n"
                + "      mcmp_period=$(cat \"$mcmp_cpu_path/cpu.cfs_period_us\" 2>/dev/null); then\n"
                + "        printf '" + RUNTIME_CPU_QUOTA + "%s %s\\n' \"$mcmp_quota\" \"$mcmp_period\"\n"
                + "    else mcmp_cpu_probe_ok=false\n"
                + "    fi\n"
                + "    [ \"$mcmp_cpu_path\" = \"$mcmp_cpu_root\" ] && break\n"
                + "    case \"$mcmp_cpu_path\" in \"$mcmp_cpu_root\"/*) mcmp_cpu_path=${mcmp_cpu_path%/*} ;; *) mcmp_cpu_probe_ok=false; break ;; esac\n"
                + "  done ;; esac\n"
                + ";; esac\n"
                + "printf '" + RUNTIME_CPU_PROBE_OK + "%s\\n' \"$mcmp_cpu_probe_ok\"\n";
    }

    private List<String> markerValues(String output, String marker) {
        return output.lines()
                .filter(line -> line.startsWith(marker))
                .map(line -> line.substring(marker.length()).trim())
                .toList();
    }

    private JsonNode parseInspect(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray() || root.isEmpty()) {
            throw new IllegalStateException("docker inspect returned no container");
        }
        return root.get(0);
    }

    private JsonNode parseStats(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(json.lines().findFirst().orElse("{}"));
    }

    private String extractSection(String output, String begin, String end) {
        int beginIndex = output.indexOf(begin);
        int endIndex = output.indexOf(end);
        if (beginIndex < 0 || endIndex < 0 || endIndex <= beginIndex) {
            throw new IllegalStateException("Missing structured Docker output section: " + begin);
        }
        return output.substring(beginIndex + begin.length(), endIndex).strip();
    }

    private boolean extractMarkerValue(String output, String marker) {
        return output.lines()
                .filter(line -> line.startsWith(marker))
                .map(line -> line.substring(marker.length()).trim())
                .findFirst()
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    private Integer extractServicePort(JsonNode inspect) {
        JsonNode ports = inspect.path("NetworkSettings").path("Ports");
        if (!ports.isObject()) {
            return null;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = ports.fields();
        while (fields.hasNext()) {
            JsonNode bindings = fields.next().getValue();
            if (!bindings.isArray()) {
                continue;
            }
            for (JsonNode binding : bindings) {
                String hostPort = textOrNull(binding.path("HostPort"));
                if (hostPort != null && hostPort.matches("\\d+")) {
                    int parsed = Integer.parseInt(hostPort);
                    if (parsed >= 1 && parsed <= 65_535) {
                        return parsed;
                    }
                }
            }
        }
        return null;
    }

    private Double parsePercentage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.replace("%", "").trim());
            return Double.isFinite(parsed) && parsed >= 0 ? roundTwoDecimals(parsed) : null;
        } catch (NumberFormatException e) {
            log.debug("Unable to parse Docker percentage: {}", value);
            return null;
        }
    }

    private MemoryValues parseMemoryUsage(String value) {
        String[] values = splitIoPair(value);
        return new MemoryValues(parseSizeBytes(values[0]), parseSizeBytes(values[1]));
    }

    private NetworkValues parseNetworkIo(String value) {
        String[] values = splitIoPair(value);
        Long input = parseSizeBytes(values[0]);
        Long output = parseSizeBytes(values[1]);
        return new NetworkValues(
                input == null ? null : input.doubleValue(),
                output == null ? null : output.doubleValue());
    }

    private String[] splitIoPair(String value) {
        if (value == null) {
            return new String[] {null, null};
        }
        String[] parts = value.split("\\s*/\\s*", -1);
        return parts.length == 2 ? parts : new String[] {null, null};
    }

    private Long parseSizeBytes(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = SIZE_VALUE.matcher(value.trim());
        if (!matcher.matches()) {
            log.debug("Unable to parse Docker byte size: {}", value);
            return null;
        }
        double amount = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        double multiplier = switch (unit) {
            case "b" -> 1;
            case "kb" -> 1_000;
            case "mb" -> 1_000_000;
            case "gb" -> 1_000_000_000;
            case "tb" -> 1_000_000_000_000L;
            case "pb" -> 1_000_000_000_000_000L;
            case "kib" -> 1_024;
            case "mib" -> 1_048_576;
            case "gib" -> 1_073_741_824;
            case "tib" -> 1_099_511_627_776L;
            case "pib" -> 1_125_899_906_842_624L;
            default -> 1;
        };
        return Math.round(amount * multiplier);
    }

    private String mapContainerStatus(String state) {
        if (state == null) {
            return "UNKNOWN";
        }
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "running" -> "RUNNING";
            case "exited", "dead", "created" -> "STOPPED";
            case "restarting" -> "RESTARTING";
            case "paused" -> "PAUSED";
            default -> "UNKNOWN";
        };
    }

    private String validateContainerId(String containerId) {
        if (containerId == null || !CONTAINER_ID.matcher(containerId.trim()).matches()) {
            throw new IllegalArgumentException("Invalid Docker container ID");
        }
        return containerId.trim();
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record MemoryValues(Long usedBytes, Long limitBytes) {
    }

    private record NetworkValues(Double inputBytes, Double outputBytes) {
    }
}
