# Rebuild daily archive summaries

The native-query aliases must match `DailyAggregationProjection` properties exactly.
With Spring Data JPA 3.2, snake_case aliases could return a null `sampleCount` and
cause every scheduled daily aggregation to be skipped even while raw metrics accumulated.

Fixing the query repairs future runs; it does not automatically fill historical gaps.
`backfill-daily-metrics.sql` rebuilds only selected deployments and completed days
from **real retained measurements**. It never synthesizes measurements or deletes raw data.
It uses the current scheduler's formulas, including 10 minutes per sample and its
inclusive 23:59:59 cutoff. Days without raw samples are not invented or overwritten.

1. Back up the database (`pg_dump -Fc`) and verify the backup's contents.
2. Use the same timezone as the AM scheduler. Review IDs and the retained date range.
3. Preview with an authenticated local PostgreSQL connection:

   ```sh
   psql -X -v deployment_ids=7,11,22,24 \
     -v start_date=2026-09-15 -v end_date=2026-09-20 \
     -f scripts/archive/backfill-daily-metrics.sql
   ```

4. Repeat with `-v apply=true` to commit. The default is a rollback. Repeating the
   committed operation updates the same summaries rather than creating duplicates.
   A rollback may still advance PostgreSQL sequence values; gaps in IDs are harmless.
5. Re-run **policy recommendation analysis** for each deployment through the normal
   authenticated `POST /api/applications/{deploymentId}/policy-recommendation/analyze`
   endpoint. It recomputes the 90-, 30-, and 7-day results. Otherwise the UI may still
   show an older stored analysis. Keep existing analysis rows as historical records.
6. Check `daily_metrics_summary`, the operation-profile API, and the next scheduled
   run (02:00 aggregation; 02:10 analysis in the server timezone).

A valid analysis day currently needs at least 72 samples, at least 720 running
minutes, and a CPU or memory P95 value. Fewer than seven valid days still correctly
results in insufficient data after repair.

## Verification

`bash scripts/archive/test-backfill.sh` runs PostgreSQL 16 integration tests in a
disposable Docker container with no external network, published ports, or persistent
data volume. It covers preview rollback, repeatable updates, CPU/memory/network/event
values, null metrics, the 72-sample threshold, date/deployment scoping, source-data
preservation, and invalid-input rejection. The temporary test container is removed
on exit. It requires the `postgres:16-alpine` image to be available or downloadable.

`bash gradlew test --tests '*ResourceMetricsHistoryRepositoryTest'` tests the actual
Spring Data native projection against isolated in-memory data, including every
projection field, empty/single-sample data and 71/72/137/144-sample cases.

## Normalize proven legacy VM CPU history

`normalize-legacy-vm-cpu.py` is an **explicit per-deployment maintenance tool**, not
an automatic migration. Old Docker CPU percentages use one core as 100%; new VM
CPU percentages use the container's effective CPU capacity as 100%. For example,
an original 87.48% becomes 43.74% with a proven capacity of two cores.

Only use it when an operator has established that the **host CPU count, CPU quota,
cpuset and effective capacity were unchanged for the entire selected history**,
and that its unversioned values really are original Docker percentages. A current
VM specification alone does not prove past capacity. If capacity is unknown or
changed, leave the old CPU values unversioned; AM excludes them from VM CPU policy
recommendations while retaining memory, network, events and archive coverage.

The tool requires Python 3, Docker access, and the updated AM database columns.
The PostgreSQL container must already define `POSTGRES_USER` and `POSTGRES_DB`.
It uses those values inside the container without printing credentials.

1. Back up and verify the database before maintenance.
2. Run on the database host, replacing the deployment ID and proven capacity:

   ```sh
   python3 scripts/archive/normalize-legacy-vm-cpu.py \
     --container mc-application-manager-db \
     --deployment-id 43 --cpu-capacity-cores 2 \
     --confirm-capacity-unchanged --confirm-legacy-docker-percent
   ```

3. Review the JSON summary. The default run **rolls back every database change**,
   including backup creation. Add `--apply` to the same command to commit.
4. Re-run the normal authenticated policy-recommendation analysis endpoint and
   verify the current metric, daily CPU values and resulting recommendation.

The transaction saves original full rows as JSON in a private, deployment-specific
schema, e.g. `am_cpu_v1_d43.raw_before` and `am_cpu_v1_d43.daily_before`. Existing
backup rows are never overwritten. `runs` records the operator's capacity
assertions and the changed-row counts. Only CPU fields in public archive tables
are updated; memory, network, sample counts, events, statuses, timestamps,
deployment records and source/provenance metadata are not changed. The tool does
not create measurements or turn generated data into real historical measurements.

Each original raw CPU value is retained in `cpu_raw_pct`; `cpu_capacity_cores` and
`cpu_metrics_version=1` record the conversion basis. Version-1 rows are skipped, so
repeat runs cannot divide them again. Unknown versions, negative/non-finite values,
values above the proven capacity, and conflicting existing raw values abort the
transaction instead of being silently clamped or guessed.

Daily CPU aggregates are rebuilt only when the complete raw sample count matches
the existing daily `sample_count`, all non-null CPU samples are normalized, and
there are at least **72 normalized CPU samples and 720 CPU-observed running
minutes**. A daily row with purged/partial raw history, insufficient CPU coverage,
or already normalized CPU is left unchanged. It is **not** linearly divided from
an assumed current VM size. Future daily aggregation uses the same CPU coverage
thresholds without affecting the overall memory/availability coverage.

The general `backfill-daily-metrics.sql` repair tool uses the same VM CPU masking,
version and coverage rules as the scheduler. A rebuild updates the CPU version
alongside its values; it cannot leave a stale normalized marker on a mixed or
legacy-only day. K8S CPU aggregation remains unchanged. Its PostgreSQL integration
fixture includes normalized, legacy-only, one-CPU-sample, mixed, and 710/720-minute
boundary cases.

For a scoped rollback, review the corresponding private backup rows and restore
only the raw CPU fields (`cpu_usage_pct`, `cpu_raw_pct`, `cpu_capacity_cores`,
`cpu_metrics_version`) and daily CPU fields (`avg_cpu_pct`, `max_cpu_pct`,
`p95_cpu_pct`, `stddev_cpu`, `cpu_metrics_version`) by their saved IDs. Do not
overwrite subsequent operator changes or restore unrelated tables. Keep the
verified pre-maintenance database dump as an additional recovery option.

Run the offline CLI/SQL safety tests with:

```sh
python3 scripts/archive/test-normalize-legacy-vm-cpu.py
```
