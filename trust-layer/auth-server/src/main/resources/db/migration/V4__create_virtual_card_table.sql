CREATE TABLE virtual_card (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_identity_id UUID NOT NULL REFERENCES user_identity(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    bound_credential_id UUID NOT NULL REFERENCES biometric_credential(id),
    pan_token VARCHAR(255) NOT NULL,
    last_four VARCHAR(4) NOT NULL,
    card_network VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'ETB',
    daily_limit_minor BIGINT NOT NULL DEFAULT 500000,
    single_tx_limit_minor BIGINT NOT NULL DEFAULT 100000,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expiry_date DATE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    provisioned_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_vcard_user ON virtual_card(user_identity_id);
CREATE INDEX idx_vcard_tenant ON virtual_card(tenant_id);
