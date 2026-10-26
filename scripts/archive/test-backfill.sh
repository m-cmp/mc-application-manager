#!/usr/bin/env bash
# Integration test against an isolated, disposable PostgreSQL 16 instance.
# No host ports, production credentials, persistent volumes or external network.
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
test_container=$(docker run -d --rm --network none \
  --tmpfs /var/lib/postgresql/data \
  -e POSTGRES_HOST_AUTH_METHOD=trust postgres:16-alpine)
trap 'docker rm -f "$test_container" >/dev/null' EXIT
for attempt in {1..30}; do
  if docker exec "$test_container" pg_isready -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
sql() { docker exec -i "$test_container" psql -X -v ON_ERROR_STOP=1 -U postgres "$@"; }
backfill() {
  sql -v deployment_ids=1,2,3 -v start_date=2000-01-01 -v end_date=2000-01-01 "$@" \
    < "$script_dir/backfill-daily-metrics.sql"
}
sql <<'SQL'
CREATE TABLE deployment_history (id bigint PRIMARY KEY);
INSERT INTO deployment_history VALUES (1), (2), (3), (4);
CREATE TABLE resource_metrics_history (
  deployment_id bigint, recorded_at timestamp, cpu_usage_pct double precision,
  memory_usage_pct double precision, network_in_bytes bigint, network_out_bytes bigint,
  oom_killed boolean, status text, resource_type text
);
CREATE TABLE abnormal_event (deployment_id bigint, occurred_at timestamp, event_type text);
CREATE TABLE daily_metrics_summary (
  deployment_id bigint, summary_date date, avg_cpu_pct double precision,
  max_cpu_pct double precision, p95_cpu_pct double precision, stddev_cpu double precision,
  avg_memory_pct double precision, max_memory_pct double precision,
  p95_memory_pct double precision, stddev_memory double precision,
  avg_network_in_bytes double precision, max_network_in_bytes bigint,
  avg_network_out_bytes double precision, max_network_out_bytes bigint,
  sample_count integer, oom_count integer, restart_count integer, crash_loop_count integer,
  running_minutes integer, total_minutes integer, resource_type text, created_at timestamp,
  UNIQUE (deployment_id, summary_date)
);
INSERT INTO resource_metrics_history VALUES
  (1, '2000-01-01 00:00:00', 20, 40, 100, 300, false, 'RUNNING', 'GENERAL'),
  (1, '2000-01-01 00:10:00', 40, 80, 200, 500, true, 'STOPPED', 'GENERAL'),
  (1, '2000-01-02 00:00:00', 99, 99, 999, 999, true, 'RUNNING', 'GENERAL'),
  (4, '2000-01-01 00:00:00', 99, 99, 999, 999, true, 'RUNNING', 'GENERAL');
INSERT INTO resource_metrics_history
  SELECT 2, timestamp '2000-01-01' + i * interval '10 minutes', NULL, 0,
         NULL, NULL, NULL, 'RUNNING', 'GENERAL' FROM generate_series(0,71) i;
INSERT INTO abnormal_event VALUES
  (1, '2000-01-01', 'OOM_KILLED'), (1, '2000-01-01', 'RESTART'),
  (1, '2000-01-01', 'CRASH_LOOP'), (1, '2000-01-02', 'RESTART');
ALTER TABLE resource_metrics_history ADD COLUMN deployment_type text DEFAULT 'K8S';
ALTER TABLE resource_metrics_history ADD COLUMN cpu_metrics_version integer;
ALTER TABLE daily_metrics_summary ADD COLUMN cpu_metrics_version integer;
SQL
backfill
test "$(sql -Atc 'SELECT count(*) FROM daily_metrics_summary')" = 0
backfill -v apply=true
backfill -v apply=true
sql <<'SQL'
DO $$ BEGIN
  IF (SELECT count(*) FROM daily_metrics_summary) <> 2 THEN RAISE EXCEPTION 'Duplicate or invented days'; END IF;
  IF NOT EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id=1
      AND sample_count=2 AND avg_cpu_pct=30 AND max_cpu_pct=40 AND abs(p95_cpu_pct-39)<0.001
      AND abs(stddev_cpu-sqrt(200))<0.001 AND avg_memory_pct=60 AND p95_memory_pct=78
      AND avg_network_in_bytes=150 AND max_network_out_bytes=500
      AND oom_count=2 AND restart_count=1 AND crash_loop_count=1
      AND running_minutes=10 AND total_minutes=20) THEN RAISE EXCEPTION 'Aggregate mismatch'; END IF;
  IF NOT EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id=2
      AND sample_count=72 AND running_minutes=720 AND p95_cpu_pct IS NULL AND p95_memory_pct=0)
      THEN RAISE EXCEPTION 'Missing metrics or valid-day threshold mismatch'; END IF;
  IF (SELECT count(*) FROM resource_metrics_history) <> 76 THEN RAISE EXCEPTION 'Raw metrics changed'; END IF;
  IF (SELECT count(*) FROM abnormal_event) <> 4 THEN RAISE EXCEPTION 'Events changed'; END IF;
END $$;
SQL
if backfill -v deployment_ids=999 -v apply=true; then echo 'Unknown ID accepted' >&2; exit 1; fi
if backfill -v start_date=2000-01-02 -v apply=true; then echo 'Reversed dates accepted' >&2; exit 1; fi
if backfill -v end_date=2999-01-01 -v apply=true; then echo 'Incomplete future day accepted' >&2; exit 1; fi
if backfill -v deployment_ids= -v apply=true; then echo 'Empty IDs accepted' >&2; exit 1; fi
test "$(sql -Atc 'SELECT count(*) FROM daily_metrics_summary')" = 2
sql <<'SQL'
INSERT INTO deployment_history VALUES (5), (6), (7), (8), (9);
INSERT INTO resource_metrics_history
 (deployment_id, recorded_at, cpu_usage_pct, memory_usage_pct, status, resource_type,
  deployment_type, cpu_metrics_version)
 SELECT 5, timestamp '2000-01-01' + i * interval '10 minutes', 40, 40, 'RUNNING',
        'GENERAL', 'DOCKER', 1 FROM generate_series(0,71) i;
INSERT INTO resource_metrics_history
 (deployment_id, recorded_at, cpu_usage_pct, memory_usage_pct, status, resource_type,
  deployment_type, cpu_metrics_version)
 SELECT 6, timestamp '2000-01-01' + i * interval '10 minutes', 180, 40, 'RUNNING',
        'GENERAL', 'VM', NULL FROM generate_series(0,143) i;
INSERT INTO resource_metrics_history
 (deployment_id, recorded_at, cpu_usage_pct, memory_usage_pct, status, resource_type,
  deployment_type, cpu_metrics_version)
 SELECT 7, timestamp '2000-01-01' + i * interval '10 minutes', CASE WHEN i=0 THEN 40 END,
        40, 'RUNNING', 'GENERAL', 'DOCKER', CASE WHEN i=0 THEN 1 END FROM generate_series(0,143) i;
INSERT INTO resource_metrics_history
 (deployment_id, recorded_at, cpu_usage_pct, memory_usage_pct, status, resource_type,
  deployment_type, cpu_metrics_version)
 SELECT 8, timestamp '2000-01-01' + i * interval '10 minutes', CASE WHEN i<72 THEN 40 ELSE 180 END,
        40, 'RUNNING', 'GENERAL', 'DOCKER', CASE WHEN i<72 THEN 1 END FROM generate_series(0,143) i;
INSERT INTO resource_metrics_history
 (deployment_id, recorded_at, cpu_usage_pct, memory_usage_pct, status, resource_type,
  deployment_type, cpu_metrics_version)
 SELECT 9, timestamp '2000-01-01' + i * interval '10 minutes', 40, 40,
        CASE WHEN i<71 THEN 'RUNNING' ELSE 'STOPPED' END, 'GENERAL', 'VM', 1 FROM generate_series(0,71) i;
SQL
backfill -v deployment_ids=5,6,7,8,9 -v apply=true
sql <<'SQL'
-- A later rebuild must also clear a stale normalized marker if CPU coverage is not valid.
UPDATE daily_metrics_summary SET cpu_metrics_version=1 WHERE deployment_id=6;
SQL
backfill -v deployment_ids=5,6,7,8,9 -v apply=true
sql <<'SQL'
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id=5
      AND p95_cpu_pct=40 AND cpu_metrics_version=1 AND sample_count=72 AND running_minutes=720)
      THEN RAISE EXCEPTION 'Normalized CPU boundary mismatch'; END IF;
  IF NOT EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id=6
      AND p95_cpu_pct IS NULL AND cpu_metrics_version IS NULL AND p95_memory_pct=40
      AND sample_count=144 AND running_minutes=1440)
      THEN RAISE EXCEPTION 'Legacy CPU leaked into normalized aggregates'; END IF;
  IF NOT EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id=7
      AND p95_cpu_pct=40 AND cpu_metrics_version IS NULL AND sample_count=144)
      THEN RAISE EXCEPTION 'One CPU sample cannot qualify as a complete CPU day'; END IF;
  IF NOT EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id=8
      AND p95_cpu_pct=40 AND cpu_metrics_version IS NULL AND sample_count=144)
      THEN RAISE EXCEPTION 'Mixed legacy and normalized day cannot qualify'; END IF;
  IF NOT EXISTS (SELECT 1 FROM daily_metrics_summary WHERE deployment_id=9
      AND cpu_metrics_version IS NULL AND running_minutes=710)
      THEN RAISE EXCEPTION 'CPU running-minute threshold mismatch'; END IF;
  IF (SELECT count(*) FROM resource_metrics_history) <> 652 THEN RAISE EXCEPTION 'VM raw metrics changed'; END IF;
END $$;
SQL
echo 'PASS: dry-run, aggregation, null metrics, CPU version/coverage parity, scoping, repeatability, source preservation and invalid input guards'
