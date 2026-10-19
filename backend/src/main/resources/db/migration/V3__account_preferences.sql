ALTER TABLE app_users
    ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC';
ALTER TABLE app_users
    ADD COLUMN incident_emails_enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_users
    ADD COLUMN recovery_emails_enabled BOOLEAN NOT NULL DEFAULT TRUE;
