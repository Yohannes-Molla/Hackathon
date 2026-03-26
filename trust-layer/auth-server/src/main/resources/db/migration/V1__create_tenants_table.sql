CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    domain VARCHAR(255),
    mtls_cert_thumbprint VARCHAR(64),
    primary_color VARCHAR(7) DEFAULT '#6366f1',
    logo_url VARCHAR(2048),
    custom_css TEXT,
    trust_framework VARCHAR(50) NOT NULL DEFAULT 'et_nbe_kyc',
    is_hub BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
