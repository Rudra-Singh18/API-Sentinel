CREATE TABLE status_pages (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    title VARCHAR(100) NOT NULL,
    slug VARCHAR(64) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_status_pages_slug UNIQUE (slug)
);
CREATE INDEX ix_status_pages_owner ON status_pages(owner_id, created_at DESC);

CREATE TABLE status_page_monitors (
    status_page_id UUID NOT NULL REFERENCES status_pages(id) ON DELETE CASCADE,
    monitor_id UUID NOT NULL REFERENCES monitors(id) ON DELETE CASCADE,
    PRIMARY KEY (status_page_id, monitor_id)
);