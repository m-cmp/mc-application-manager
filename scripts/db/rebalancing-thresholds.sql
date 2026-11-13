-- PostgreSQL: apply to the Application Manager database (default: appmgr),
-- after its base schema and before starting a version that reads these fields.
-- Existing rows keep NULL and continue using their prior threshold values.
ALTER TABLE deployment_history
    ADD COLUMN IF NOT EXISTS rebalancing_cpu_threshold double precision,
    ADD COLUMN IF NOT EXISTS rebalancing_memory_threshold double precision;
