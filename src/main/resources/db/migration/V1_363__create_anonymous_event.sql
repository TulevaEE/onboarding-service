-- Events from people who are not logged in (login started/failed, public page views).
-- Kept apart from event_log, which holds only actions of authenticated people.
CREATE TABLE anonymous_event
(
    id        BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP   NOT NULL,
    type      VARCHAR(40) NOT NULL,
    visit_id  VARCHAR(64) NOT NULL,
    device    VARCHAR(10),
    data      JSONB       NOT NULL
);

CREATE INDEX anonymous_event_visit_id_index ON anonymous_event (visit_id);
CREATE INDEX anonymous_event_type_timestamp_index ON anonymous_event (type, timestamp);
