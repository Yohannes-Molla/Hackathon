# Business and Deployment Model

## Target Customers

- Banks and digital banks
- Fintech lenders
- Merchant acquirers and payment facilitators
- Government-backed inclusion programs

## Value Proposition

- Portable identity + eKYC rails
- Biometric-bound transaction authorization
- Multi-tenant federation through centralized identity infrastructure

## Deployment Topology

- Containerized microservices behind a gateway
- PostgreSQL for transactional data
- Redis for session/event coordination
- Keycloak for identity
- Vault for tokenization/secrets

## Monetization Direction

- Per-tenant platform fee
- Per-verification and per-transaction infrastructure fee
- Optional premium modules (risk analytics, compliance exports)
