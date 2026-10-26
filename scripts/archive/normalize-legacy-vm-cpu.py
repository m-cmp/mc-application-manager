#!/usr/bin/env python3
"""Explicit, reversible CPU-only migration for one proven legacy Docker deployment.

Uses the PostgreSQL container's existing POSTGRES_USER/POSTGRES_DB environment;
no credentials, extra Python packages, or AM service restarts are required.
"""

import argparse
from decimal import Decimal, InvalidOperation
import re
import subprocess
import sys


def positive_deployment_id(value):
    try:
        parsed = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("deployment ID must be a positive integer") from exc
    if parsed <= 0 or parsed > 9223372036854775807:
        raise argparse.ArgumentTypeError("deployment ID must be a positive bigint")
    return parsed


def positive_capacity(value):
    try:
        parsed = Decimal(value)
    except InvalidOperation as exc:
        raise argparse.ArgumentTypeError("CPU capacity must be a positive finite number") from exc
    if not parsed.is_finite() or parsed <= 0 or parsed > Decimal("65536"):
        raise argparse.ArgumentTypeError("CPU capacity must be finite and in (0, 65536] cores")
    # Numeric-only SQL literal; no floats, exponent notation, or user-controlled SQL.
    return format(parsed, "f")


def container_name(value):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,127}", value):
        raise argparse.ArgumentTypeError("invalid Docker container name or ID")
    return value


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--container", required=True, type=container_name,
                        help="existing PostgreSQL container (not the AM container)")
    parser.add_argument("--deployment-id", required=True, type=positive_deployment_id)
    parser.add_argument("--cpu-capacity-cores", required=True, type=positive_capacity,
                        help="proven effective CPU capacity for the entire retained history")
    parser.add_argument("--confirm-capacity-unchanged", action="store_true",
                        help="confirm host CPU, quotas and cpusets did not change during this history")
    parser.add_argument("--confirm-legacy-docker-percent", action="store_true",
                        help="confirm unversioned VM CPU values are original Docker core percentages")
    parser.add_argument("--apply", action="store_true",
                        help="commit; without this flag all database changes are rolled back")
    args = parser.parse_args(argv)
    if not args.confirm_capacity_unchanged or not args.confirm_legacy_docker_percent:
        parser.error("both --confirm-capacity-unchanged and --confirm-legacy-docker-percent are required")
    return args


def build_sql(deployment_id, capacity, apply=False):
    """Return one transaction; all substitutions have strict numeric validation."""
    deployment_id = positive_deployment_id(str(deployment_id))
    capacity = positive_capacity(str(capacity))
    schema = f"am_cpu_v1_d{deployment_id}"
    ending = "COMMIT" if apply else "ROLLBACK"
    mode = "APPLY" if apply else "DRY_RUN"
    return f"""
BEGIN;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET LOCAL statement_timeout = '60s';
SET LOCAL lock_timeout = '10s';
SELECT pg_advisory_xact_lock(193247, ({deployment_id} % 2147483647)::integer);

DO $check$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.deployment_history
                   WHERE id = {deployment_id} AND UPPER(deployment_type) = 'VM') THEN
        RAISE EXCEPTION 'Target deployment must exist and have deployment_type VM';
    END IF;
    IF EXISTS (SELECT 1 FROM public.resource_metrics_history
               WHERE deployment_id = {deployment_id}
                 AND (deployment_type IS NULL OR UPPER(deployment_type) NOT IN ('VM', 'DOCKER'))) THEN
        RAISE EXCEPTION 'Target contains raw records that are not explicitly VM/DOCKER';
    END IF;
    IF EXISTS (SELECT 1 FROM public.resource_metrics_history
               WHERE deployment_id = {deployment_id}
                 AND cpu_metrics_version IS NOT NULL AND cpu_metrics_version <> 1)
       OR EXISTS (SELECT 1 FROM public.daily_metrics_summary
                   WHERE deployment_id = {deployment_id}
                     AND cpu_metrics_version IS NOT NULL AND cpu_metrics_version <> 1) THEN
        RAISE EXCEPTION 'Unknown CPU metrics version: migration aborted';
    END IF;
    IF EXISTS (SELECT 1 FROM public.resource_metrics_history
               WHERE deployment_id = {deployment_id} AND cpu_metrics_version IS NULL
                 AND cpu_usage_pct IS NOT NULL
                 AND NOT (cpu_usage_pct >= 0 AND cpu_usage_pct <= 100 * {capacity}::double precision)) THEN
        RAISE EXCEPTION 'Legacy CPU value is negative, non-finite, or above the proven capacity';
    END IF;
    IF EXISTS (SELECT 1 FROM public.resource_metrics_history
               WHERE deployment_id = {deployment_id} AND cpu_metrics_version = 1
                 AND cpu_usage_pct IS NOT NULL
                 AND NOT (cpu_usage_pct >= 0 AND cpu_usage_pct <= 100)) THEN
        RAISE EXCEPTION 'Existing normalized CPU value is outside [0, 100]';
    END IF;
    IF EXISTS (SELECT 1 FROM public.resource_metrics_history
               WHERE deployment_id = {deployment_id} AND cpu_metrics_version IS NULL
                 AND cpu_usage_pct IS NOT NULL AND cpu_raw_pct IS NOT NULL
                 AND cpu_raw_pct IS DISTINCT FROM cpu_usage_pct) THEN
        RAISE EXCEPTION 'Unversioned CPU has a conflicting raw value; manual inspection required';
    END IF;
END
$check$;

-- Row locks cover only the selected deployment; no existing cloud resource is touched.
DO $lock$
BEGIN
    PERFORM id FROM public.resource_metrics_history
     WHERE deployment_id = {deployment_id} ORDER BY id FOR UPDATE;
    PERFORM id FROM public.daily_metrics_summary
     WHERE deployment_id = {deployment_id} ORDER BY id FOR UPDATE;
END
$lock$;

CREATE SCHEMA IF NOT EXISTS {schema};
REVOKE ALL ON SCHEMA {schema} FROM PUBLIC;
CREATE TABLE IF NOT EXISTS {schema}.raw_before (
    id bigint PRIMARY KEY,
    row_data jsonb NOT NULL,
    backed_up_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE IF NOT EXISTS {schema}.daily_before (
    id bigint PRIMARY KEY,
    row_data jsonb NOT NULL,
    backed_up_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE IF NOT EXISTS {schema}.runs (
    run_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    capacity_cores numeric NOT NULL,
    legacy_docker_percent_confirmed boolean NOT NULL,
    capacity_unchanged_confirmed boolean NOT NULL,
    converted_raw_count bigint NOT NULL,
    rebuilt_daily_count bigint NOT NULL
);
REVOKE ALL ON ALL TABLES IN SCHEMA {schema} FROM PUBLIC;

CREATE TEMP TABLE cpu_raw_plan ON COMMIT DROP AS
 SELECT id FROM public.resource_metrics_history
  WHERE deployment_id = {deployment_id}
    AND UPPER(deployment_type) IN ('VM', 'DOCKER')
    AND cpu_metrics_version IS NULL AND cpu_usage_pct IS NOT NULL;

INSERT INTO {schema}.raw_before (id, row_data)
 SELECT metrics.id, to_jsonb(metrics)
   FROM public.resource_metrics_history metrics JOIN cpu_raw_plan plan USING (id)
 ON CONFLICT (id) DO NOTHING;

UPDATE public.resource_metrics_history metrics
   SET cpu_raw_pct = metrics.cpu_usage_pct,
       cpu_capacity_cores = {capacity}::double precision,
       cpu_usage_pct = metrics.cpu_usage_pct / {capacity}::double precision,
       cpu_metrics_version = 1
  FROM cpu_raw_plan plan WHERE metrics.id = plan.id;

-- Do not derive a daily P95 from a partial retained day, nor divide an
-- already normalized daily value a second time. Purged raw days stay unknown.
CREATE TEMP TABLE cpu_daily_plan ON COMMIT DROP AS
 SELECT summary.id,
        AVG(metrics.cpu_usage_pct) AS avg_cpu_pct,
        MAX(metrics.cpu_usage_pct) AS max_cpu_pct,
        PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY metrics.cpu_usage_pct) AS p95_cpu_pct,
        STDDEV(metrics.cpu_usage_pct) AS stddev_cpu
   FROM public.daily_metrics_summary summary
   JOIN public.resource_metrics_history metrics
     ON metrics.deployment_id = summary.deployment_id
    AND metrics.recorded_at BETWEEN summary.summary_date::timestamp
          AND summary.summary_date::timestamp + INTERVAL '23 hours 59 minutes 59 seconds'
  WHERE summary.deployment_id = {deployment_id}
    AND summary.cpu_metrics_version IS NULL
  GROUP BY summary.id, summary.sample_count
 HAVING COUNT(*) = summary.sample_count
    AND summary.sample_count > 0
    AND COUNT(metrics.cpu_usage_pct) >= 72
    AND COUNT(CASE WHEN metrics.cpu_usage_pct IS NOT NULL AND metrics.cpu_metrics_version = 1
                        AND metrics.status = 'RUNNING' THEN 1 END) * 10 >= 720
    AND COUNT(CASE WHEN metrics.cpu_usage_pct IS NOT NULL
                        AND (metrics.cpu_metrics_version IS NULL OR metrics.cpu_metrics_version <> 1)
                   THEN 1 END) = 0;

INSERT INTO {schema}.daily_before (id, row_data)
 SELECT summary.id, to_jsonb(summary)
   FROM public.daily_metrics_summary summary JOIN cpu_daily_plan plan USING (id)
 ON CONFLICT (id) DO NOTHING;

UPDATE public.daily_metrics_summary summary
   SET avg_cpu_pct = plan.avg_cpu_pct,
       max_cpu_pct = plan.max_cpu_pct,
       p95_cpu_pct = plan.p95_cpu_pct,
       stddev_cpu = plan.stddev_cpu,
       cpu_metrics_version = 1
  FROM cpu_daily_plan plan WHERE summary.id = plan.id;

INSERT INTO {schema}.runs
 (capacity_cores, legacy_docker_percent_confirmed, capacity_unchanged_confirmed,
  converted_raw_count, rebuilt_daily_count)
 SELECT {capacity}, true, true, (SELECT COUNT(*) FROM cpu_raw_plan), (SELECT COUNT(*) FROM cpu_daily_plan);

SELECT json_build_object(
    'mode', '{mode}', 'deployment_id', {deployment_id}, 'cpu_capacity_cores', {capacity},
    'converted_raw_rows', (SELECT COUNT(*) FROM cpu_raw_plan),
    'rebuilt_daily_rows', (SELECT COUNT(*) FROM cpu_daily_plan),
    'remaining_unversioned_daily_rows', (SELECT COUNT(*) FROM public.daily_metrics_summary
        WHERE deployment_id = {deployment_id} AND cpu_metrics_version IS NULL),
    'backup_schema', '{schema}', 'committed', {'true' if apply else 'false'});
{ending};
"""


def main(argv=None):
    args = parse_args(argv)
    sql = build_sql(args.deployment_id, args.cpu_capacity_cores, args.apply)
    command = [
        "docker", "exec", "-i", args.container, "sh", "-c",
        ': "${POSTGRES_USER:?POSTGRES_USER must be configured}"; '
        ': "${POSTGRES_DB:?POSTGRES_DB must be configured}"; '
        'exec psql -X -q -A -t -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"',
    ]
    try:
        result = subprocess.run(command, input=sql, text=True, capture_output=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired) as exc:
        print(f"Migration could not run: {exc}", file=sys.stderr)
        return 1
    # SQL lock SELECTs return IDs. Report only the JSON summary, not record contents.
    for line in result.stdout.splitlines():
        if line.strip().startswith("{"):
            print(line)
    if result.stderr:
        print(result.stderr.rstrip(), file=sys.stderr)
    if result.returncode:
        print("Migration failed; the transaction was not committed.", file=sys.stderr)
    elif not args.apply:
        print("Dry run rolled back. Re-run with --apply only after reviewing the result.")
    return result.returncode


if __name__ == "__main__":
    raise SystemExit(main())
