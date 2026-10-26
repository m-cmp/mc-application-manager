#!/usr/bin/env python3
"""No Docker or database required: CLI and SQL safety contract tests."""

from contextlib import redirect_stderr, redirect_stdout
import importlib.util
from io import StringIO
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "normalize_legacy_vm_cpu", Path(__file__).with_name("normalize-legacy-vm-cpu.py"))
migration = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(migration)


class CpuMigrationTest(unittest.TestCase):
    def args(self, *extra):
        return ["--container", "mc-application-manager-db", "--deployment-id", "43",
                "--cpu-capacity-cores", "2", "--confirm-capacity-unchanged",
                "--confirm-legacy-docker-percent", *extra]

    def test_defaults_to_rollback_without_apply(self):
        args = migration.parse_args(self.args())
        self.assertFalse(args.apply)
        sql = migration.build_sql(args.deployment_id, args.cpu_capacity_cores, args.apply)
        self.assertTrue(sql.rstrip().endswith("ROLLBACK;"))
        self.assertIn("'committed', false", sql)
        self.assertNotIn("COMMIT;", sql)

    def test_apply_requires_explicit_flag(self):
        args = migration.parse_args(self.args("--apply"))
        self.assertTrue(args.apply)
        self.assertTrue(migration.build_sql(args.deployment_id, args.cpu_capacity_cores, args.apply)
                        .rstrip().endswith("COMMIT;"))

    def test_both_operator_confirmations_are_mandatory_even_for_preview(self):
        for missing in ["--confirm-capacity-unchanged", "--confirm-legacy-docker-percent"]:
            with self.subTest(missing=missing), redirect_stderr(StringIO()), self.assertRaises(SystemExit):
                migration.parse_args([arg for arg in self.args() if arg != missing])

    def test_rejects_invalid_capacity_without_running_sql(self):
        for value in ["0", "-1", "NaN", "sNaN", "Infinity", "-Infinity", "abc", "2;DROP TABLE x", "65537"]:
            with self.subTest(value=value), self.assertRaises(Exception):
                migration.positive_capacity(value)

    def test_preserves_fractional_and_multi_core_capacity(self):
        for value, expected in [("0.5", "0.5"), ("2", "2"), ("4", "4"), ("2e0", "2")]:
            with self.subTest(value=value):
                self.assertEqual(migration.positive_capacity(value), expected)

    def test_rejects_invalid_ids_and_container_arguments(self):
        for value in ["0", "-1", "1;SELECT 1", "9223372036854775808"]:
            with self.subTest(value=value), self.assertRaises(Exception):
                migration.positive_deployment_id(value)
        for value in ["", "-i", "db;echo secret", "db/name", "db name"]:
            with self.subTest(value=value), self.assertRaises(Exception):
                migration.container_name(value)

    def test_preserves_raw_source_and_has_per_deployment_private_backups(self):
        sql = migration.build_sql(43, "2")
        self.assertIn("CREATE SCHEMA IF NOT EXISTS am_cpu_v1_d43", sql)
        self.assertIn("REVOKE ALL ON SCHEMA am_cpu_v1_d43 FROM PUBLIC", sql)
        self.assertIn("to_jsonb(metrics)", sql)
        self.assertIn("to_jsonb(summary)", sql)
        self.assertIn("cpu_raw_pct = metrics.cpu_usage_pct", sql)
        self.assertIn("cpu_capacity_cores = 2::double precision", sql)
        self.assertIn("cpu_usage_pct = metrics.cpu_usage_pct / 2::double precision", sql)
        self.assertIn("ON CONFLICT (id) DO NOTHING", sql)

    def test_idempotence_and_version_safety_are_part_of_every_sql_plan(self):
        sql = migration.build_sql(43, "2")
        self.assertIn("cpu_metrics_version IS NULL AND cpu_usage_pct IS NOT NULL", sql)
        self.assertIn("summary.cpu_metrics_version IS NULL", sql)
        self.assertIn("cpu_metrics_version IS NOT NULL AND cpu_metrics_version <> 1", sql)
        self.assertIn("Unknown CPU metrics version: migration aborted", sql)
        self.assertIn("WHERE metrics.id = plan.id", sql)
        self.assertIn("WHERE summary.id = plan.id", sql)

    def test_daily_rebuild_requires_complete_source_and_sufficient_normalized_cpu(self):
        sql = migration.build_sql(43, "2")
        self.assertIn("HAVING COUNT(*) = summary.sample_count", sql)
        self.assertIn("COUNT(metrics.cpu_usage_pct) >= 72", sql)
        self.assertIn("AND metrics.status = 'RUNNING' THEN 1 END) * 10 >= 720", sql)
        self.assertIn("metrics.cpu_metrics_version IS NULL OR metrics.cpu_metrics_version <> 1", sql)
        self.assertIn("PERCENTILE_CONT(0.95)", sql)
        self.assertIn("STDDEV(metrics.cpu_usage_pct)", sql)
        self.assertNotIn("SET avg_memory_pct", sql)
        self.assertNotIn("SET recorded_at", sql)
        self.assertNotIn("SET executed_at", sql)

    def test_out_of_range_data_aborts_instead_of_clamping(self):
        sql = migration.build_sql(43, "2")
        self.assertIn("cpu_usage_pct >= 0 AND cpu_usage_pct <= 100 * 2::double precision", sql)
        self.assertIn("RAISE EXCEPTION 'Legacy CPU value", sql)
        self.assertNotIn("LEAST(", sql)
        self.assertNotIn("GREATEST(", sql)

    def test_manual_backfill_preserves_cpu_version_and_coverage_contract(self):
        sql = Path(__file__).with_name("backfill-daily-metrics.sql").read_text()
        self.assertIn("THEN NULL ELSE cpu_usage_pct END AS comparable_cpu_pct", sql)
        self.assertIn("avg(comparable_cpu_pct)", sql)
        self.assertIn("cpu_usage_pct IS NOT NULL AND cpu_metrics_version = 1) >= 72", sql)
        self.assertIn("AND status = 'RUNNING') * 10 >= 720", sql)
        self.assertIn("cpu_metrics_version = excluded.cpu_metrics_version", sql)

    @patch.object(migration.subprocess, "run")
    def test_command_uses_container_credentials_without_interpolating_them(self, run):
        run.return_value = subprocess.CompletedProcess([], 0, '{"mode":"DRY_RUN"}\n', "")
        with redirect_stdout(StringIO()) as output:
            self.assertEqual(migration.main(self.args()), 0)
        command = run.call_args.args[0]
        self.assertEqual(command[:5], ["docker", "exec", "-i", "mc-application-manager-db", "sh"])
        self.assertIn('"$POSTGRES_USER"', command[-1])
        self.assertIn('"$POSTGRES_DB"', command[-1])
        self.assertIn("ON_ERROR_STOP=1", command[-1])
        self.assertEqual(run.call_args.kwargs["timeout"], 120)
        self.assertIn("Dry run rolled back", output.getvalue())

    @patch.object(migration.subprocess, "run")
    def test_database_error_is_returned_without_claiming_success(self, run):
        run.return_value = subprocess.CompletedProcess([], 3, "", "ERROR: unknown version")
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()) as error:
            self.assertEqual(migration.main(self.args("--apply")), 3)
        self.assertIn("not committed", error.getvalue())


if __name__ == "__main__":
    unittest.main()
