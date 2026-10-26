#!/usr/bin/env bash
# CPU migration integration tests in isolated PostgreSQL 16; no AM/CSP resources.
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
test_container=$(docker run -d --rm --network none \
  --tmpfs /var/lib/postgresql/data -e POSTGRES_HOST_AUTH_METHOD=trust \
  -e POSTGRES_USER=postgres -e POSTGRES_DB=postgres postgres:16-alpine)
trap 'docker rm -f "$test_container" >/dev/null' EXIT
for attempt in {1..30}; do
  if docker exec "$test_container" pg_isready -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
sql() { docker exec -i "$test_container" psql -X -qAt -v ON_ERROR_STOP=1 -U postgres "$@"; }
normalize() {
  PYTHONDONTWRITEBYTECODE=1 python3 "$script_dir/normalize-legacy-vm-cpu.py" \
    --container "$test_container" --cpu-capacity-cores 2 \
    --confirm-capacity-unchanged --confirm-legacy-docker-percent "$@"
}
sql <<'SQL'
CREATE TABLE deployment_history (id bigint PRIMARY KEY, deployment_type text, executed_at timestamp);
INSERT INTO deployment_history
 SELECT i, CASE WHEN i=2 THEN 'K8S' ELSE 'VM' END, timestamp '2000-01-01' FROM generate_series(1,10) i;
CREATE TABLE resource_metrics_history (
  id bigserial PRIMARY KEY, deployment_id bigint, recorded_at timestamp,
  cpu_usage_pct double precision, cpu_raw_pct double precision,
  cpu_capacity_cores double precision, cpu_metrics_version integer,
  memory_usage_pct double precision DEFAULT 20, network_in_bytes bigint DEFAULT 100,
  network_out_bytes bigint DEFAULT 300, status text DEFAULT 'RUNNING',
  deployment_type text DEFAULT 'DOCKER', source_marker text DEFAULT 'unchanged'
);
CREATE TABLE daily_metrics_summary (
  id bigserial PRIMARY KEY, deployment_id bigint, summary_date date,
  avg_cpu_pct double precision, max_cpu_pct double precision, p95_cpu_pct double precision,
  stddev_cpu double precision, cpu_metrics_version integer,
  sample_count integer, p95_memory_pct double precision DEFAULT 20,
  max_network_in_bytes bigint DEFAULT 100, max_network_out_bytes bigint DEFAULT 300,
  running_minutes integer DEFAULT 1440, created_at timestamp DEFAULT timestamp '2000-01-02',
  UNIQUE (deployment_id, summary_date)
);
INSERT INTO resource_metrics_history (deployment_id, recorded_at, cpu_usage_pct)
 SELECT 1, timestamp '2000-01-01' + i * interval '10 minutes', 87.48 FROM generate_series(0,143) i;
UPDATE resource_metrics_history
 SET cpu_usage_pct=43.74, cpu_raw_pct=87.48, cpu_capacity_cores=2, cpu_metrics_version=1 WHERE id=1;
INSERT INTO resource_metrics_history (deployment_id, recorded_at, cpu_usage_pct, cpu_metrics_version)
 VALUES (3, timestamp '2000-01-01', 20, 2),
        (4, timestamp '2000-01-01', 201, NULL),
        (8, timestamp '2000-01-01', -1, NULL),
        (9, timestamp '2000-01-01', 'NaN', NULL);
INSERT INTO resource_metrics_history (deployment_id, recorded_at, cpu_usage_pct)
 SELECT 6, timestamp '2000-01-01' + i * interval '10 minutes', 80 FROM generate_series(0,71) i;
INSERT INTO resource_metrics_history (deployment_id, recorded_at, cpu_usage_pct)
 SELECT 7, timestamp '2000-01-01' + i * interval '10 minutes', CASE WHEN i=0 THEN 80 END
 FROM generate_series(0,143) i;
INSERT INTO resource_metrics_history (deployment_id, recorded_at, cpu_usage_pct)
 SELECT 10, timestamp '2000-01-01' + i * interval '10 minutes', 80 FROM generate_series(0,71) i;
UPDATE resource_metrics_history SET status='STOPPED' WHERE deployment_id=10
 AND recorded_at=timestamp '2000-01-01 11:50:00';
INSERT INTO daily_metrics_summary
 (deployment_id, summary_date, avg_cpu_pct, max_cpu_pct, p95_cpu_pct, stddev_cpu, sample_count)
 SELECT i, date '2000-01-01', 87.48, 87.48, 87.48, 0, 144 FROM unnest(ARRAY[1,5,6,7]) i;
INSERT INTO daily_metrics_summary
 (deployment_id, summary_date, avg_cpu_pct, max_cpu_pct, p95_cpu_pct, stddev_cpu, sample_count)
 VALUES (10, date '2000-01-01', 80, 80, 80, 0, 72);
SQL
raw_non_cpu_before=$(sql -c "SELECT md5(string_agg((to_jsonb(m)-'cpu_usage_pct'-'cpu_raw_pct'-'cpu_capacity_cores'-'cpu_metrics_version')::text, ',' ORDER BY id)) FROM resource_metrics_history m")
daily_non_cpu_before=$(sql -c "SELECT md5(string_agg((to_jsonb(d)-'avg_cpu_pct'-'max_cpu_pct'-'p95_cpu_pct'-'stddev_cpu'-'cpu_metrics_version')::text, ',' ORDER BY id)) FROM daily_metrics_summary d")
normalize --deployment-id 1
test "$(sql -c 'SELECT cpu_usage_pct FROM resource_metrics_history WHERE id=2')" = 87.48
test "$(sql -c "SELECT count(*) FROM pg_namespace WHERE nspname='am_cpu_v1_d1'")" = 0
normalize --deployment-id 1 --apply
normalize --deployment-id 1 --apply
sql <<'SQL'
DO $$ BEGIN
 IF (SELECT count(*) FROM resource_metrics_history WHERE deployment_id=1 AND cpu_metrics_version=1
     AND cpu_raw_pct=87.48 AND cpu_capacity_cores=2 AND cpu_usage_pct=43.74) <> 144
     THEN RAISE EXCEPTION 'Raw conversion or idempotence mismatch'; END IF;
 IF NOT EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id=1
     AND cpu_metrics_version=1 AND abs(avg_cpu_pct-43.74)<0.000001
     AND max_cpu_pct=43.74 AND p95_cpu_pct=43.74)
     THEN RAISE EXCEPTION 'Daily rebuild mismatch'; END IF;
 IF (SELECT count(*) FROM am_cpu_v1_d1.raw_before) <> 143
     OR (SELECT count(*) FROM am_cpu_v1_d1.daily_before) <> 1
     THEN RAISE EXCEPTION 'Scoped original backup mismatch'; END IF;
 IF NOT EXISTS (SELECT 1 FROM am_cpu_v1_d1.raw_before WHERE id=2
     AND (row_data->>'cpu_usage_pct')::double precision=87.48
     AND row_data->>'cpu_metrics_version' IS NULL)
     THEN RAISE EXCEPTION 'Original backup was overwritten'; END IF;
END $$;
SQL
normalize --deployment-id 5 --apply
normalize --deployment-id 6 --apply
normalize --deployment-id 7 --apply
normalize --deployment-id 10 --apply
sql <<'SQL'
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id IN (5,6,7,10)
     AND cpu_metrics_version IS NOT NULL)
     THEN RAISE EXCEPTION 'Purged/partial/CPU-sparse/710-minute day falsely normalized'; END IF;
 IF EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id IN (5,6,7)
     AND p95_cpu_pct IS DISTINCT FROM 87.48::double precision)
     THEN RAISE EXCEPTION 'Unsafe linear division of incomplete daily data'; END IF;
END $$;
SQL
for invalid_id in 2 3 4 8 9 999; do
  if normalize --deployment-id "$invalid_id" --apply; then
    echo "Unsafe deployment $invalid_id accepted" >&2
    exit 1
  fi
done
test "$raw_non_cpu_before" = "$(sql -c "SELECT md5(string_agg((to_jsonb(m)-'cpu_usage_pct'-'cpu_raw_pct'-'cpu_capacity_cores'-'cpu_metrics_version')::text, ',' ORDER BY id)) FROM resource_metrics_history m")"
test "$daily_non_cpu_before" = "$(sql -c "SELECT md5(string_agg((to_jsonb(d)-'avg_cpu_pct'-'max_cpu_pct'-'p95_cpu_pct'-'stddev_cpu'-'cpu_metrics_version')::text, ',' ORDER BY id)) FROM daily_metrics_summary d")"
test "$(sql -c 'SELECT count(*) FROM am_cpu_v1_d1.runs')" = 2
echo 'PASS: CPU migration dry-run/apply/idempotence/backups, complete/sparse/partial/purged days, range/version guards and non-CPU preservation'
