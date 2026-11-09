-- Heartbeat log for background loops on databases created before the table existed.
--
-- Fresh installs create `job_runs` in the V1 baseline. Databases that were
-- already running when that table was folded into the baseline never received
-- a patch for it, so the night-audit maintenance tick's
-- `DELETE FROM job_runs …` fails every day with "relation job_runs does not
-- exist", and `core::job_runs` stays silent (its once-per-process probe finds
-- the table missing). Idempotent: a database that already has the table is
-- unchanged.

CREATE TABLE IF NOT EXISTS public.job_runs (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_name character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    detail jsonb,
    error text,
    duration_ms integer,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_job_runs_job_created
    ON public.job_runs USING btree (job_name, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_job_runs_created
    ON public.job_runs USING btree (created_at DESC);
