CREATE TABLE biometric_credential (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_identity_id UUID NOT NULL REFERENCES user_identity(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    key_id VARCHAR(255) NOT NULL UNIQUE,
    public_key_jwk TEXT NOT NULL,
    jwk_thumbprint VARCHAR(64) NOT NULL UNIQUE,
    device_id VARCHAR(255) NOT NULL,
    attestation_certificate TEXT,
    credential_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    registered_at TIMESTAMP NOT NULL DEFAULT now(),
    last_used_at TIMESTAMP,
    revoked_at TIMESTAMP
);
CREATE INDEX idx_biometric_key_id ON biometric_credential(key_id);
CREATE INDEX idx_biometric_user ON biometric_credential(user_identity_id);
