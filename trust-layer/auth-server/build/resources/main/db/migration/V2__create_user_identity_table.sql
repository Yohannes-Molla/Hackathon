CREATE TABLE user_identity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    external_sub VARCHAR(255) NOT NULL,
    given_name VARCHAR(100) NOT NULL,
    family_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    nationality VARCHAR(3) NOT NULL,
    assurance_level VARCHAR(10) NOT NULL DEFAULT 'ial2',
    ekyc_verification_id VARCHAR(255),
    trust_framework VARCHAR(50) NOT NULL,
    onboarding_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, external_sub)
);
CREATE INDEX idx_user_identity_tenant ON user_identity(tenant_id);
CREATE INDEX idx_user_identity_state ON user_identity(onboarding_state);
