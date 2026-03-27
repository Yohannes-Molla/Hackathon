-- Stable Keycloak user IDs (see deploy/keycloak/realm-trust-layer.json). Fresh compose + import only.
-- Idempotent: safe to re-run Flyway on existing DB.

INSERT INTO user_identity (id, tenant_id, external_sub, keycloak_sub, given_name, family_name, date_of_birth, nationality, assurance_level, ekyc_verification_id, trust_framework, onboarding_state, risk_score, version, created_at, updated_at)
SELECT 'd0000000-0000-4000-8000-000000000001', '11111111-1111-1111-1111-111111111111', 'd0000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'Demo', 'User', '1990-01-01', 'ETH', 'ial2', 'seed_ver_demo', 'et_nbe_kyc', 'LIVE', 12, 0, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM user_identity WHERE keycloak_sub = 'd0000000-0000-4000-8000-000000000001');

INSERT INTO user_identity (id, tenant_id, external_sub, keycloak_sub, given_name, family_name, date_of_birth, nationality, assurance_level, ekyc_verification_id, trust_framework, onboarding_state, risk_score, version, created_at, updated_at)
SELECT 'd0000000-0000-4000-8000-000000000002', '11111111-1111-1111-1111-111111111111', 'd0000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000002', 'Admin', 'User', '1985-06-15', 'ETH', 'ial2', 'seed_ver_admin', 'et_nbe_kyc', 'EKYC_COMPLETE', 5, 0, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM user_identity WHERE keycloak_sub = 'd0000000-0000-4000-8000-000000000002');

INSERT INTO user_identity (id, tenant_id, external_sub, keycloak_sub, given_name, family_name, date_of_birth, nationality, assurance_level, ekyc_verification_id, trust_framework, onboarding_state, risk_score, version, created_at, updated_at)
SELECT 'd0000000-0000-4000-8000-000000000003', '11111111-1111-1111-1111-111111111111', 'd0000000-0000-4000-8000-000000000003', 'd0000000-0000-4000-8000-000000000003', 'Merchant', 'User', '1988-03-20', 'ETH', 'ial2', 'seed_ver_merch', 'et_nbe_kyc', 'EKYC_COMPLETE', 8, 0, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM user_identity WHERE keycloak_sub = 'd0000000-0000-4000-8000-000000000003');

INSERT INTO biometric_credential (id, user_identity_id, tenant_id, key_id, public_key_jwk, jwk_thumbprint, device_id, attestation_certificate, credential_type, status, registered_at)
SELECT 'c0000000-0000-4000-8000-000000000001'::uuid, 'd0000000-0000-4000-8000-000000000001'::uuid, '11111111-1111-1111-1111-111111111111'::uuid, 'demo-seed-key-1',
       '{"kty":"EC","crv":"P-256","x":"weNJy1HAX1LGyhtUpUokLfbW0gRH8_pRwj9AhdOyDNk","y":"WoohpyTv3NmM0IwIPNQ08E9dR7AkWpQJO8KNYZ4FYU8"}',
       '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
       'seed-device', 'demo-attestation', 'SIGNING', 'ACTIVE', now()
WHERE EXISTS (SELECT 1 FROM user_identity WHERE id = 'd0000000-0000-4000-8000-000000000001')
  AND NOT EXISTS (SELECT 1 FROM biometric_credential WHERE key_id = 'demo-seed-key-1');

INSERT INTO virtual_card (id, user_identity_id, tenant_id, bound_credential_id, pan_token, last_four, card_network, currency, daily_limit_minor, single_tx_limit_minor, status, expiry_date, version, provisioned_at, updated_at)
SELECT 'f0000000-0000-4000-8000-000000000001'::uuid, 'd0000000-0000-4000-8000-000000000001'::uuid, '11111111-1111-1111-1111-111111111111'::uuid, 'c0000000-0000-4000-8000-000000000001'::uuid,
       'vault_tok_seed_demo_0001', '4242', 'VISA', 'ETB', 500000, 100000, 'ACTIVE', (CURRENT_DATE + interval '3 years')::date, 0, now(), now()
WHERE EXISTS (SELECT 1 FROM biometric_credential WHERE id = 'c0000000-0000-4000-8000-000000000001')
  AND NOT EXISTS (SELECT 1 FROM virtual_card WHERE id = 'f0000000-0000-4000-8000-000000000001');

INSERT INTO transactions (id, tenant_id, user_identity_id, virtual_card_id, merchant_id, amount_minor, currency, status, nonce, signature_verified, risk_score, fraud_score, fraud_flags, version, created_at)
SELECT 't0000000-0000-4000-8000-000000000001'::uuid, '11111111-1111-1111-1111-111111111111'::uuid, 'd0000000-0000-4000-8000-000000000001'::uuid, 'f0000000-0000-4000-8000-000000000001'::uuid,
       'd0000000-0000-4000-8000-000000000003', 25000, 'ETB', 'APPROVED', 'seed-nonce-demo-1', true, 10, 10, '', 0, now()
WHERE EXISTS (SELECT 1 FROM virtual_card WHERE id = 'f0000000-0000-4000-8000-000000000001')
  AND NOT EXISTS (SELECT 1 FROM transactions WHERE nonce = 'seed-nonce-demo-1');
