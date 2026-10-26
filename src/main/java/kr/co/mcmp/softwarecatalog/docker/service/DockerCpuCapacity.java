package kr.co.mcmp.softwarecatalog.docker.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/** Resolves Docker CPU capacity without assuming that an unknown host has one CPU. */
final class DockerCpuCapacity {

    static final int METRICS_VERSION = 1;
    // Docker's rounded, independently sampled counter/quota windows can overshoot slightly.
    // Only one percentage point is tolerated; raw values remain available for inspection.
    private static final double SAMPLING_TOLERANCE_PERCENT = 1.0;

    private DockerCpuCapacity() {
    }

    static Double resolve(JsonNode config, String hostCpuCount, String allowedCpuList,
            List<String> runtimeQuotas) {
        try {
            if (config == null || !config.isObject() || hostCpuCount == null
                    || !hostCpuCount.matches("[1-9][0-9]*")) {
                return null;
            }
            double capacity = Long.parseLong(hostCpuCount);
            if (config.hasNonNull("NanoCpus")) {
                long nanoCpus = nonNegativeLong(config.get("NanoCpus"));
                if (nanoCpus > 0) {
                    capacity = Math.min(capacity, nanoCpus / 1_000_000_000.0);
                }
            }
            if (config.hasNonNull("CpuQuota")) {
                long quota = strictLong(config.get("CpuQuota"));
                if (quota < -1) {
                    return null;
                }
                if (quota > 0) {
                    long period = nonNegativeLong(config.path("CpuPeriod"));
                    if (period == 0) {
                        // Docker's default period when only CpuQuota is configured.
                        period = 100_000;
                    }
                    capacity = Math.min(capacity, quota / (double) period);
                }
            }
            String configuredSet = config.path("CpusetCpus").asText("").trim();
            if (!configuredSet.isEmpty()) {
                capacity = Math.min(capacity, countCpuList(configuredSet));
            }
            if (allowedCpuList != null && !allowedCpuList.isBlank()) {
                capacity = Math.min(capacity, countCpuList(allowedCpuList));
            }
            for (String runtimeQuota : runtimeQuotas) {
                String[] pair = runtimeQuota.trim().split("\\s+");
                if (pair.length != 2) {
                    return null;
                }
                long period = Long.parseLong(pair[1]);
                if (period <= 0) {
                    return null;
                }
                if ("max".equals(pair[0]) || "-1".equals(pair[0])) {
                    continue;
                }
                long quota = Long.parseLong(pair[0]);
                if (quota <= 0) {
                    return null;
                }
                capacity = Math.min(capacity, quota / (double) period);
            }
            return Double.isFinite(capacity) && capacity > 0 ? capacity : null;
        } catch (IllegalArgumentException | ArithmeticException e) {
            return null;
        }
    }

    /** The source is always the raw Docker value; never normalize a normalized value again. */
    static Double normalize(Double rawCpuUsage, Double capacity) {
        if (rawCpuUsage == null || !Double.isFinite(rawCpuUsage) || rawCpuUsage < 0
                || capacity == null || !Double.isFinite(capacity) || capacity <= 0) {
            return null;
        }
        double percent = rawCpuUsage / capacity;
        // Large inconsistencies must not become a plausible-looking clamped value.
        if (!Double.isFinite(percent) || percent > 100 + SAMPLING_TOLERANCE_PERCENT) {
            return null;
        }
        return Math.round(Math.min(percent, 100) * 100.0) / 100.0;
    }

    static long countCpuList(String value) {
        List<long[]> ranges = new ArrayList<>();
        for (String item : value.trim().split(",", -1)) {
            if (!item.matches("[0-9]+(?:-[0-9]+)?")) {
                throw new IllegalArgumentException("Invalid CPU set");
            }
            String[] limits = item.split("-", -1);
            long first = Long.parseLong(limits[0]);
            long last = limits.length == 1 ? first : Long.parseLong(limits[1]);
            if (last < first || last > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid CPU set range");
            }
            ranges.add(new long[] {first, last});
        }
        ranges.sort(Comparator.comparingLong(range -> range[0]));
        long count = 0;
        long first = ranges.get(0)[0];
        long last = ranges.get(0)[1];
        for (int i = 1; i < ranges.size(); i++) {
            long[] range = ranges.get(i);
            if (range[0] <= last + 1) {
                last = Math.max(last, range[1]);
            } else {
                count += last - first + 1;
                first = range[0];
                last = range[1];
            }
        }
        return count + last - first + 1;
    }

    private static long nonNegativeLong(JsonNode value) {
        long parsed = strictLong(value);
        if (parsed < 0) {
            throw new IllegalArgumentException("Negative CPU setting");
        }
        return parsed;
    }

    private static long strictLong(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("Invalid CPU setting");
        }
        return value.longValue();
    }
}
