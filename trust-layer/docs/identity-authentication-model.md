# Identity and Authentication Model

## Identity Provider

Keycloak is the OIDC authority for login, consent, sessions, and token issuance.

## Roles

- `user`: end-user dashboard and identity claims
- `admin`: tenant/user/risk/audit operations
- `merchant`: payment initiation and reconciliation operations

## Token Model

- OAuth2 Authorization Code + PKCE for web/mobile clients
- JWT access tokens validated by gateway and services
- Optional DPoP checks on high-assurance endpoints

## Tenant Model

- Tenant represented in `tenants` table
- User identity linked to tenant by `tenant_id`
- Token claims and gateway routing enforce tenant isolation

## Session Model

- User session list and revocation exposed by `auth-server` session endpoints
- Keycloak remains source of truth for browser/mobile sessions

