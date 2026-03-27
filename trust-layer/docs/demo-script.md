# 5-Minute Demo Script (Web)

Stack: `cd deploy && docker compose up -d` — Frontend `http://localhost:3000`, Gateway `http://localhost:8080`, Keycloak `http://localhost:8180`. Users: `demo`/`demo`, `admin`/`admin`, `merchant`/`merchant`.

## 0:00 - 0:45 Identity and SSO

1. Open the web portal and sign in through Keycloak (`demo`).
2. Show role-based navigation (user areas; `admin` and `merchant` links when those roles are present).

## 0:45 - 1:45 eKYC and AI

1. As `demo`, open **Identity**.
2. Under **Web eKYC**, click **Start eKYC session**, then upload a document image (ID or any test image).
3. Show the JSON result (VERIFIED vs PENDING_REVIEW) and refreshed onboarding state on the same page.

## 1:45 - 2:45 Card Provisioning

1. Open **Cards**.
2. Click **Provision virtual card** (registers a browser P-256 key with demo attestation, then calls provision).
3. Show card list, limits, freeze/unfreeze, and on **Dashboard** the rotating **dynamic CVV** (polls every 60s).

## 2:45 - 4:00 Merchant and Transaction

1. Sign in as **merchant** / open **Merchant** portal.
2. Confirm **Payer Keycloak subject** matches the customer (default is the seeded demo sub; or copy from customer **Identity** page).
3. **Create Challenge**, then sign in as **demo** → **Transactions** → **Pending payment challenges** → **Approve**.
4. Back to merchant: **Transaction History** and **Reconciliation** show the approved payment.

## 4:00 - 5:00 Admin and Security

1. Sign in as **admin** → **Admin** portal: tenants, users, risk summary, audit logs.
2. Optional: call an API without `Authorization` and show JSON `{"error":"unauthorized"}` (not an HTML error page).
3. Conclude with architecture (gateway → services → Postgres/Redis/Keycloak/Vault/AI) and `README` local run.

## Optional: Android

The home-page QR flow remains for future mobile integration; the web path above is the primary hackathon demo.
