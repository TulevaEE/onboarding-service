-- =============================================================================
-- PostgreSQL-only: fills instrument_reference_history (created in V1_249).
--
-- Lives in classpath:/db/pg, wired into the Flyway locations for every non-H2
-- profile only, because H2 cannot parse plpgsql. The V1_249_1 version is
-- deliberate — it sorts between V1_249 and a future V1_250, so it can never
-- collide with a migration someone later adds to db/migration. On H2 the
-- history table therefore exists but stays empty.
-- =============================================================================

CREATE OR REPLACE FUNCTION record_instrument_reference_change() RETURNS trigger AS $$
BEGIN
    IF (TG_OP = 'DELETE') THEN
        INSERT INTO instrument_reference_history
            (instrument_reference_id, isin, operation, old_values, new_values, changed_by, changed_at)
        VALUES (OLD.id, OLD.isin, 'DELETE', to_jsonb(OLD), NULL, session_user, now());
        RETURN OLD;
    ELSIF (TG_OP = 'UPDATE') THEN
        INSERT INTO instrument_reference_history
            (instrument_reference_id, isin, operation, old_values, new_values, changed_by, changed_at)
        VALUES (NEW.id, NEW.isin, 'UPDATE', to_jsonb(OLD), to_jsonb(NEW), session_user, now());
        RETURN NEW;
    ELSE
        INSERT INTO instrument_reference_history
            (instrument_reference_id, isin, operation, old_values, new_values, changed_by, changed_at)
        VALUES (NEW.id, NEW.isin, 'INSERT', NULL, to_jsonb(NEW), session_user, now());
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER instrument_reference_history_trigger
    AFTER INSERT OR UPDATE OR DELETE ON instrument_reference
    FOR EACH ROW EXECUTE FUNCTION record_instrument_reference_change();
