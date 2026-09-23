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
