CREATE ALIAS IF NOT EXISTS pg_advisory_xact_lock AS $$
void pg_advisory_xact_lock(long key) {}
$$;
