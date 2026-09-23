-- PostgreSQL / psql only. Back up the database first.
-- Required variables: deployment_ids (comma-separated IDs), start_date, end_date.
-- end_date is inclusive and MUST be before CURRENT_DATE (database/server timezone).
-- Dry-run by default; add -v apply=true only after reviewing the result.
-- Rebuilds derived daily summaries only. Raw metrics, events and logs are untouched.
\set ON_ERROR_STOP on
\if :{?apply}
\else
  \set apply false
\endif
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';
CREATE TEMP TABLE archive_backfill_scope ON COMMIT DROP AS
SELECT string_to_array(:'deployment_ids', ',')::bigint[] AS ids,
       :'start_date'::date AS first_day, :'end_date'::date AS last_day;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM archive_backfill_scope
             WHERE cardinality(ids) IS NULL OR cardinality(ids) = 0
                OR first_day IS NULL OR last_day IS NULL
                OR first_day > last_day OR last_day >= CURRENT_DATE) THEN
    RAISE EXCEPTION 'Provide deployment IDs and a valid completed-day range';
  END IF;
  IF EXISTS (SELECT 1 FROM archive_backfill_scope s, unnest(s.ids) AS target(id)
             WHERE NOT EXISTS (SELECT 1 FROM deployment_history d WHERE d.id = target.id)) THEN
    RAISE EXCEPTION 'Unknown deployment ID';
  END IF;
END $$;

WITH raw AS (
  SELECT m.deployment_id, m.recorded_at::date AS summary_date,
         avg(cpu_usage_pct) AS avg_cpu_pct, max(cpu_usage_pct) AS max_cpu_pct,
         percentile_cont(0.95) WITHIN GROUP (ORDER BY cpu_usage_pct) AS p95_cpu_pct,
         stddev(cpu_usage_pct) AS stddev_cpu,
         avg(memory_usage_pct) AS avg_memory_pct, max(memory_usage_pct) AS max_memory_pct,
         percentile_cont(0.95) WITHIN GROUP (ORDER BY memory_usage_pct) AS p95_memory_pct,
         stddev(memory_usage_pct) AS stddev_memory,
         avg(network_in_bytes) AS avg_network_in_bytes, max(network_in_bytes) AS max_network_in_bytes,
         avg(network_out_bytes) AS avg_network_out_bytes, max(network_out_bytes) AS max_network_out_bytes,
         count(*)::integer AS sample_count,
         (count(*) FILTER (WHERE oom_killed = true))::integer AS oom_count,
         (count(*) FILTER (WHERE status = 'RUNNING') * 10)::integer AS running_minutes,
         (count(*) * 10)::integer AS total_minutes, min(resource_type) AS resource_type
    FROM resource_metrics_history m CROSS JOIN archive_backfill_scope s
   WHERE m.deployment_id = ANY(s.ids)
     AND m.recorded_at >= s.first_day AND m.recorded_at < s.last_day + 1
     -- Match the current scheduler's inclusive 23:59:59 upper bound exactly.
     AND m.recorded_at <= m.recorded_at::date + time '23:59:59'
   GROUP BY m.deployment_id, m.recorded_at::date
), events AS (
  SELECT e.deployment_id, e.occurred_at::date AS summary_date,
         (count(*) FILTER (WHERE event_type = 'OOM_KILLED'))::integer AS oom_count,
         (count(*) FILTER (WHERE event_type = 'RESTART'))::integer AS restart_count,
         (count(*) FILTER (WHERE event_type = 'CRASH_LOOP'))::integer AS crash_loop_count
    FROM abnormal_event e CROSS JOIN archive_backfill_scope s
   WHERE e.deployment_id = ANY(s.ids)
     AND e.occurred_at >= s.first_day AND e.occurred_at < s.last_day + 1
     AND e.occurred_at <= e.occurred_at::date + time '23:59:59'
   GROUP BY e.deployment_id, e.occurred_at::date
)
INSERT INTO daily_metrics_summary (
  deployment_id, summary_date, avg_cpu_pct, max_cpu_pct, p95_cpu_pct, stddev_cpu,
  avg_memory_pct, max_memory_pct, p95_memory_pct, stddev_memory,
  avg_network_in_bytes, max_network_in_bytes, avg_network_out_bytes, max_network_out_bytes,
  sample_count, oom_count, restart_count, crash_loop_count, running_minutes, total_minutes,
  resource_type, created_at
)
SELECT r.deployment_id, r.summary_date, avg_cpu_pct, max_cpu_pct, p95_cpu_pct, stddev_cpu,
       avg_memory_pct, max_memory_pct, p95_memory_pct, stddev_memory,
       avg_network_in_bytes, max_network_in_bytes, avg_network_out_bytes, max_network_out_bytes,
       sample_count, r.oom_count + coalesce(e.oom_count, 0), coalesce(e.restart_count, 0),
       coalesce(e.crash_loop_count, 0), running_minutes, total_minutes, resource_type, localtimestamp
  FROM raw r LEFT JOIN events e USING (deployment_id, summary_date)
ON CONFLICT (deployment_id, summary_date) DO UPDATE SET
  avg_cpu_pct = excluded.avg_cpu_pct, max_cpu_pct = excluded.max_cpu_pct,
  p95_cpu_pct = excluded.p95_cpu_pct, stddev_cpu = excluded.stddev_cpu,
  avg_memory_pct = excluded.avg_memory_pct, max_memory_pct = excluded.max_memory_pct,
  p95_memory_pct = excluded.p95_memory_pct, stddev_memory = excluded.stddev_memory,
  avg_network_in_bytes = excluded.avg_network_in_bytes, max_network_in_bytes = excluded.max_network_in_bytes,
  avg_network_out_bytes = excluded.avg_network_out_bytes, max_network_out_bytes = excluded.max_network_out_bytes,
  sample_count = excluded.sample_count, oom_count = excluded.oom_count,
  restart_count = excluded.restart_count, crash_loop_count = excluded.crash_loop_count,
  running_minutes = excluded.running_minutes, total_minutes = excluded.total_minutes,
  resource_type = excluded.resource_type;

SELECT d.deployment_id, min(summary_date) AS first_day, max(summary_date) AS last_day,
       count(*) AS summary_days, sum(sample_count) AS samples,
       count(*) FILTER (WHERE sample_count >= 72 AND running_minutes >= 720
         AND (p95_cpu_pct IS NOT NULL OR p95_memory_pct IS NOT NULL)) AS valid_days
  FROM daily_metrics_summary d CROSS JOIN archive_backfill_scope s
 WHERE d.deployment_id = ANY(s.ids) AND summary_date BETWEEN s.first_day AND s.last_day
 GROUP BY d.deployment_id ORDER BY d.deployment_id;
\if :apply
  COMMIT;
\else
  ROLLBACK;
  \echo 'DRY RUN ONLY: summaries rolled back. Sequences may advance; no source records changed.'
\endif
