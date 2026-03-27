ALTER TABLE user_identity
    ADD COLUMN IF NOT EXISTS keycloak_sub VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_identity_tenant_keycloak_sub
    ON user_identity(tenant_id, keycloak_sub)
    WHERE keycloak_sub IS NOT NULL;
