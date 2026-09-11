-- H2 cannot evaluate a CHECK constraint that was added by ALTER TABLE. Once a second Spring
-- context opens the shared in-memory database, every INSERT or UPDATE on the constrained table
-- fails with 23514 ("check constraint invalid" — the expression threw) and an empty constraint
-- expression in the message, whatever values the statement carries. It reproduces only in the
-- full suite, never when a test class runs alone, and PostgreSQL is unaffected.
--
-- The constraints therefore stay in place everywhere the application actually runs, and are
-- dropped for H2 only. InstrumentReferenceRulesTest asserts the same rules over the rows, so
-- the invariants are still checked while the suite runs on H2, and the constraints themselves
-- are exercised by the pg/ci profiles.
ALTER TABLE instrument_reference
    DROP CONSTRAINT IF EXISTS instrument_reference_instrument_type_check;
ALTER TABLE instrument_reference
    DROP CONSTRAINT IF EXISTS instrument_reference_asset_class_check;
ALTER TABLE benchmark_category_proxy
    DROP CONSTRAINT IF EXISTS benchmark_category_proxy_index_target_check;
