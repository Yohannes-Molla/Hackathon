# Security and Compliance Framework

## Threat Model (STRIDE)

- Spoofing: biometric anti-spoof and attestation checks
- Tampering: signed transaction payloads and nonce replay protection
- Repudiation: audit logging for state-changing actions
- Information disclosure: masked PAN and role-guarded APIs
- Denial of service: rate limits and bounded session challenge TTL
- Elevation of privilege: role checks for admin/merchant endpoints

## Controls

- OIDC JWT validation at service boundaries
- DPoP validation for token-bound requests
- Vault-backed tokenization for card artifacts
- Redis nonce/session expiry to reduce replay windows

## Compliance Alignment

- KYC portability support via reusable verified claims
- Auditability through `audit_logs`
- Privacy-by-default via selective disclosure pattern
