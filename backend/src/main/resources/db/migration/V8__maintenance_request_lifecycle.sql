ALTER TABLE request_history DROP CONSTRAINT ck_request_history_event_type;

ALTER TABLE request_history ADD CONSTRAINT ck_request_history_event_type CHECK (event_type IN (
    'REQUEST_CREATED',
    'REQUEST_ASSIGNED',
    'REQUEST_REASSIGNED',
    'REQUEST_STARTED',
    'REQUEST_HELD',
    'REQUEST_RESUMED',
    'REQUEST_RESOLVED',
    'REQUEST_CLOSED',
    'REQUEST_REOPENED',
    'REQUEST_REJECTED',
    'REQUEST_MARKED_DUPLICATE'
));

-- V1 permits no duplicate chain: the referenced original must not itself be a duplicate, and a
-- request cannot reference itself. The self-reference half is enforceable as a row CHECK; the
-- "original is not DUPLICATE" half needs the other row, so it stays an application rule.
ALTER TABLE maintenance_request ADD CONSTRAINT ck_maintenance_request_duplicate_not_self
    CHECK (duplicate_of_id IS NULL OR duplicate_of_id <> id);

-- A DUPLICATE request must name its original, and only a DUPLICATE request may have one.
ALTER TABLE maintenance_request ADD CONSTRAINT ck_maintenance_request_duplicate_status
    CHECK ((status = 'DUPLICATE' AND duplicate_of_id IS NOT NULL)
        OR (status <> 'DUPLICATE' AND duplicate_of_id IS NULL));

-- resolved_at and closed_at are written by resolve and close. A CLOSED request is always resolved
-- first, so a closed row carries both, and closing never precedes resolving.
ALTER TABLE maintenance_request ADD CONSTRAINT ck_maintenance_request_closed_after_resolved
    CHECK (closed_at IS NULL OR (resolved_at IS NOT NULL AND resolved_at <= closed_at));

CREATE INDEX ix_maintenance_request_status_resolved_at ON maintenance_request (status, resolved_at);
