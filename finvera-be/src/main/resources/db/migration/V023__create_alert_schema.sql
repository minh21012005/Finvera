-- Feature 032: owner-scoped deterministic alerts and in-app notifications.
CREATE TABLE alert_definition (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, name VARCHAR(120) NOT NULL,
 condition_type VARCHAR(40) NOT NULL, condition_version VARCHAR(40) NOT NULL,
 condition_payload JSONB NOT NULL, condition_summary VARCHAR(500) NOT NULL,
 enabled BOOLEAN NOT NULL, episode_state VARCHAR(12) NOT NULL DEFAULT 'UNKNOWN',
 episode_sequence BIGINT NOT NULL DEFAULT 0, baseline_at TIMESTAMPTZ NOT NULL,
 last_fact_key VARCHAR(200), last_fact_at TIMESTAMPTZ, last_evaluated_at TIMESTAMPTZ,
 last_outcome VARCHAR(16), last_reason_code VARCHAR(64), last_triggered_at TIMESTAMPTZ,
 last_delivery_outcome VARCHAR(16), next_evaluation_at TIMESTAMPTZ,
 lease_token UUID, lease_until TIMESTAMPTZ, attempt_count SMALLINT NOT NULL DEFAULT 0,
 row_version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL, deleted_at TIMESTAMPTZ,
 CONSTRAINT uq_alert_definition_owner_id UNIQUE(id,owner_id),
 CHECK(length(btrim(name)) BETWEEN 1 AND 120), CHECK(jsonb_typeof(condition_payload)='object'),
 CHECK(episode_state IN ('UNKNOWN','FALSE','TRUE') AND episode_sequence>=0),
 CHECK(last_outcome IS NULL OR last_outcome IN ('FALSE','TRUE','WITHHELD','FAILED')),
 CHECK(last_delivery_outcome IS NULL OR last_delivery_outcome IN ('DELIVERED','FAILED')),
 CHECK(attempt_count BETWEEN 0 AND 2), CHECK(deleted_at IS NULL OR enabled=false)
);
CREATE INDEX ix_alert_definition_owner_page ON alert_definition(owner_id,created_at DESC,id DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_alert_definition_due ON alert_definition(next_evaluation_at,id) WHERE enabled AND deleted_at IS NULL;
CREATE INDEX ix_alert_definition_lease ON alert_definition(lease_until) WHERE lease_token IS NOT NULL;

CREATE TABLE alert_evaluation (
 id UUID PRIMARY KEY, alert_id UUID NOT NULL, owner_id UUID NOT NULL,
 fact_key VARCHAR(200) NOT NULL, fact_at TIMESTAMPTZ, accepted_at TIMESTAMPTZ,
 evaluated_at TIMESTAMPTZ NOT NULL, outcome VARCHAR(16) NOT NULL, reason_code VARCHAR(64),
 evidence_snapshot JSONB NOT NULL, attempt_count SMALLINT NOT NULL, duration_ms INTEGER NOT NULL,
 FOREIGN KEY(alert_id,owner_id) REFERENCES alert_definition(id,owner_id),
 UNIQUE(alert_id,fact_key), CHECK(outcome IN ('FALSE','TRUE','WITHHELD','FAILED')),
 CHECK((outcome IN ('WITHHELD','FAILED') AND reason_code IS NOT NULL) OR outcome IN ('FALSE','TRUE')),
 UNIQUE(id,owner_id),
 CHECK(attempt_count BETWEEN 1 AND 2), CHECK(duration_ms>=0), CHECK(jsonb_typeof(evidence_snapshot)='object')
);
CREATE INDEX ix_alert_evaluation_page ON alert_evaluation(alert_id,evaluated_at DESC,id DESC);

CREATE TABLE alert_notification (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL, alert_id UUID NOT NULL,
 evaluation_id UUID NOT NULL, episode_sequence BIGINT, event_key VARCHAR(200),
 title VARCHAR(200) NOT NULL, message VARCHAR(1000) NOT NULL,
 condition_snapshot JSONB NOT NULL, evidence_snapshot JSONB NOT NULL,
 triggered_at TIMESTAMPTZ NOT NULL, delivered_at TIMESTAMPTZ NOT NULL, read_at TIMESTAMPTZ,
 FOREIGN KEY(alert_id,owner_id) REFERENCES alert_definition(id,owner_id),
 FOREIGN KEY(evaluation_id,owner_id) REFERENCES alert_evaluation(id,owner_id),
 CHECK((episode_sequence IS NOT NULL) <> (event_key IS NOT NULL)),
 CHECK(jsonb_typeof(condition_snapshot)='object' AND jsonb_typeof(evidence_snapshot)='object')
);
CREATE UNIQUE INDEX uq_alert_notification_episode ON alert_notification(alert_id,episode_sequence) WHERE episode_sequence IS NOT NULL;
CREATE UNIQUE INDEX uq_alert_notification_event ON alert_notification(alert_id,event_key) WHERE event_key IS NOT NULL;
CREATE INDEX ix_alert_notification_owner_page ON alert_notification(owner_id,delivered_at DESC,id DESC);
CREATE INDEX ix_alert_notification_unread ON alert_notification(owner_id,delivered_at DESC) WHERE read_at IS NULL;

CREATE TABLE alert_delivery_attempt (
 id UUID PRIMARY KEY, notification_id UUID NOT NULL REFERENCES alert_notification(id),
 channel VARCHAR(16) NOT NULL, attempt_no SMALLINT NOT NULL, outcome VARCHAR(16) NOT NULL,
 reason_code VARCHAR(64), attempted_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ NOT NULL,
 UNIQUE(notification_id,channel,attempt_no), CHECK(channel='IN_APP'),
 CHECK(attempt_no BETWEEN 1 AND 2), CHECK(outcome IN ('DELIVERED','FAILED'))
);
