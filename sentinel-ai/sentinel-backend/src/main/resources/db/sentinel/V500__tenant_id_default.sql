-- Make tenant_id default to 'default' at DB level so no insert can fail
-- even if the application layer forgets to set it
ALTER TABLE mentions ALTER COLUMN tenant_id SET DEFAULT 'default';
ALTER TABLE users    ALTER COLUMN tenant_id SET DEFAULT 'default';
