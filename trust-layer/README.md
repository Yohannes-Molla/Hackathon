# Trust Layer

Trust Layer is a hackathon prototype for federated financial identity infrastructure.

## Modules

- `auth-server`: identity bridge APIs, tenant/identity/session/admin endpoints, websocket event bridge
- `gateway`: API gateway and CORS/routing entrypoint
- `ekyc-service`: eKYC orchestration and AI verification integration
- `vci-service`: virtual card issuance, merchant registration, card controls
- `tx-service`: challenge-based signed transaction verification and history
- `common`: shared entities and DTOs
- `frontend`: user/admin/merchant portals
- `android-app`: mobile onboarding and biometric signing app
- `ai-service`: OCR, liveness, anti-spoof, and risk scoring API

## Local Run

```bash
cd deploy
docker compose up -d
```

End-to-end checklist (after services are healthy): [docs/E2E-VALIDATION.md](docs/E2E-VALIDATION.md). Demo narration: [docs/demo-script.md](docs/demo-script.md).

Primary endpoints:

- Gateway: `http://localhost:8080`
- Frontend: `http://localhost:3000`
- Keycloak: `http://localhost:8180`

## Core Flow

1. User authenticates through Keycloak.
2. Android app performs document + liveness capture.
3. eKYC service calls AI service and stores claims/risk score.
4. Credential is registered and bound to a virtual card.
5. Merchant initiates transaction; user signs with biometric key.
6. Transaction is verified, persisted, and visible across portals.
