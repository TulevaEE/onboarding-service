-- =============================================================================
-- PostgreSQL-only: fills reference_data_history (created in V1_249).
--
-- Lives in classpath:/db/pg, wired into the Flyway locations for every non-H2
-- profile only, because H2 cannot parse plpgsql. The V1_249_1 version is
-- deliberate — it sorts between V1_249 and a future V1_250, so it can never
-- collide with a migration someone later adds to db/migration. On H2 the
-- history table therefore exists but stays empty.
--
-- The function is table-agnostic: each trigger passes the column that names the
-- row to a human, so instrument_reference is audited by isin and
-- benchmark_category_proxy by benchmark_category, into one history table and
-- one notification. A trigger pointed at a column that does not exist writes a
-- NULL record_key and fails on the NOT NULL constraint instead of recording an
-- unidentifiable change.
--
-- Re-runnable like V1_249: CREATE OR REPLACE for the function, and each trigger
-- dropped by name before it is created, because PostgreSQL has no
-- CREATE TRIGGER IF NOT EXISTS.
-- =============================================================================

CREATE OR REPLACE FUNCTION record_reference_data_change() RETURNS trigger AS $$
DECLARE
    key_column text := TG_ARGV[0];
BEGIN
    IF (TG_OP = 'DELETE') THEN
        INSERT INTO reference_data_history
            (table_name, record_key, operation, old_values, new_values, changed_by, changed_at)
        VALUES (TG_TABLE_NAME, to_jsonb(OLD) ->> key_column, 'DELETE',
                to_jsonb(OLD), NULL, session_user, now());
        RETURN OLD;
    ELSIF (TG_OP = 'UPDATE') THEN
        INSERT INTO reference_data_history
            (table_name, record_key, operation, old_values, new_values, changed_by, changed_at)
        VALUES (TG_TABLE_NAME, to_jsonb(NEW) ->> key_column, 'UPDATE',
                to_jsonb(OLD), to_jsonb(NEW), session_user, now());
        RETURN NEW;
    ELSE
        INSERT INTO reference_data_history
            (table_name, record_key, operation, old_values, new_values, changed_by, changed_at)
        VALUES (TG_TABLE_NAME, to_jsonb(NEW) ->> key_column, 'INSERT',
                NULL, to_jsonb(NEW), session_user, now());
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS instrument_reference_history_trigger ON instrument_reference;
CREATE TRIGGER instrument_reference_history_trigger
    AFTER INSERT OR UPDATE OR DELETE ON instrument_reference
    FOR EACH ROW EXECUTE FUNCTION record_reference_data_change('isin');

DROP TRIGGER IF EXISTS benchmark_category_proxy_history_trigger ON benchmark_category_proxy;
CREATE TRIGGER benchmark_category_proxy_history_trigger
    AFTER INSERT OR UPDATE OR DELETE ON benchmark_category_proxy
    FOR EACH ROW EXECUTE FUNCTION record_reference_data_change('benchmark_category');
