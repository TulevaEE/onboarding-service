-- ShedLock table for distributed scheduling coordination
-- Ensures only one instance executes each scheduled job at a time

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

-- Index for efficient lock expiry queries
CREATE INDEX idx_shedlock_lock_until ON shedlock(lock_until);
