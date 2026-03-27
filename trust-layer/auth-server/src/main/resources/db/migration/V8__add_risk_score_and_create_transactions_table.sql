ALTER TABLE user_identity
    ADD COLUMN IF NOT EXISTS risk_score INTEGER;

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_identity_id UUID NOT NULL REFERENCES user_identity(id),
    virtual_card_id UUID REFERENCES virtual_card(id),
    merchant_id VARCHAR(255) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'ETB',
    status VARCHAR(20) NOT NULL,
    nonce VARCHAR(255),
    signature_verified BOOLEAN NOT NULL DEFAULT false,
    risk_score INTEGER,
    fraud_score INTEGER,
    fraud_flags TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transactions_user ON transactions(user_identity_id);
CREATE INDEX IF NOT EXISTS idx_transactions_merchant ON transactions(merchant_id);
CREATE INDEX IF NOT EXISTS idx_transactions_tenant ON transactions(tenant_id);
