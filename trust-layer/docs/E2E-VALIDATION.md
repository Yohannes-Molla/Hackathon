# End-to-end validation checklist

Run after `cd deploy && docker compose up -d` (allow ~2–3 minutes for healthchecks).

1. **Branding (gateway)**: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/tenants/hub/branding` → `200`.
2. **OIDC via frontend nginx**: `curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/realms/trust-layer/.well-known/openid-configuration` → `200`.
3. **User login**: Browser `http://localhost:3000` → Sign in `demo`/`demo` → `/dashboard` loads without console errors.
4. **Identity / eKYC**: `/identity` → Start eKYC session → upload image → see result JSON; onboarding updates.
5. **Cards**: `/cards` → Provision (if not using seeded card); limits/freeze work.
6. **Merchant + approve**: `merchant`/`merchant` → create challenge with payer sub from demo user → `demo` → `/transactions` → Approve pending challenge → merchant reconciliation updates.
7. **Admin**: `admin`/`admin` → `/admin` → tenants, risk, audit load.
8. **Test gate**: From repo root `trust-layer/`, run `./demo-test-gate.sh`.

Seeded data (Flyway `V10` + Keycloak user `id`s in `realm-trust-layer.json`) gives `demo` a pre-provisioned card and sample transaction after a **fresh** volume (`docker compose down -v`).
