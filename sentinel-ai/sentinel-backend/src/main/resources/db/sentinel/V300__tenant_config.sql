-- Tenant configuration for multi-brand support
CREATE TABLE IF NOT EXISTS tenant_config (
    tenant_id        VARCHAR(36)   PRIMARY KEY,
    brand_name       VARCHAR(255)  NOT NULL,
    handle           VARCHAR(100)  NOT NULL,
    platform         VARCHAR(50)   DEFAULT 'TWITTER',
    brand_tone       VARCHAR(500),
    ticket_system    VARCHAR(50)   DEFAULT 'MOCK',
    ticket_api_url   VARCHAR(500),
    ticket_api_key   VARCHAR(500),
    auto_reply       BOOLEAN       DEFAULT TRUE,
    require_approval BOOLEAN       DEFAULT TRUE,
    active           BOOLEAN       DEFAULT TRUE,
    created_at       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

MERGE INTO tenant_config (tenant_id, brand_name, handle, brand_tone)
KEY (tenant_id)
VALUES (
    'default',
    'Airtel Payments Bank',
    '@AirtelPaymentsBank',
    'professional,empathetic,solution-focused'
);
