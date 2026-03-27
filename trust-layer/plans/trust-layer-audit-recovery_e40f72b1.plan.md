---
name: trust-layer-audit-recovery
overview: Derive a phase-0-to-9 audit baseline from hackathon requirements and current trust-layer architecture, then produce a evidence-backed gap report plus a prioritized P0/P1/P2 recovery roadmap. After approval and if execution is enabled, implement P0 fixes incrementally with verification after each batch.
todos:
  - id: derive-phase-baseline
    content: Create explicit derived phase 0-9 mapping from requirements + module topology and state assumptions clearly.
    status: completed
  - id: compile-phase-audit
    content: Produce phase-by-phase status with concrete evidence, gaps, risks, and atomic fix actions + ETA.
    status: completed
  - id: validate-core-flows
    content: Build API/flow matrix for login, eKYC->AI->persistence, credential/card, tx approval, and admin observability.
    status: completed
  - id: security-compliance-check
    content: Audit JWT, DPoP, attestation, tokenization/vault, consent/revocation, audit logging, and privacy controls with file-level findings.
    status: completed
  - id: quality-test-gate
    content: Assess tests/build/runtime reliability and define minimum mandatory pre-demo verification gate.
    status: completed
  - id: prioritize-roadmap
    content: Produce P0/P1/P2 implementation-ready tasks with exact file targets, acceptance criteria, and commands.
    status: completed
  - id: recovery-sprint-plan
    content: Create 48-hour parallelized execution plan with subagent ownership and go/no-go hard gates/fallbacks.
    status: completed
  - id: p0-ekyc-ai-runtime-wiring
    content: P0 fix - wire eKYC docker profile and compose env so AI calls resolve to ai-service, then verify with eKYC smoke call.
    status: completed
  - id: p0-ekyc-fallback-correctness
    content: P0 fix - change eKYC fallback semantics to explicit manual-review/pending (not verified), with persistence and response consistency.
    status: completed
  - id: p0-dpop-enforcement
    content: P0 fix - wire DPoP filter into auth-server security chain and add negative/positive verification checks.
    status: completed
  - id: p0-frontend-admin-observability
    content: P0 fix - connect AdminPortal to risk-summary and audit-log APIs and remove remaining mock fallback for those views.
    status: completed
  - id: p0-android-oidc-callback
    content: P0 fix - align Android redirect URI/deeplink callback wiring and ensure token exchange executes on callback.
    status: completed
  - id: p1-proxy-env-hardening
    content: P1 fix - make frontend OIDC and websocket endpoints environment-driven and complete vite proxy routes for all backend APIs.
    status: completed
  - id: p1-minimum-test-gate
    content: P1 quality - add minimum pre-demo test gate (core unit + integration smoke) and remove test-skipping from release-critical verification path.
    status: completed
isProject: false
---

# Trust-Layer Delivery Audit and Recovery Plan

## Scope Baseline (derived since original plan is unavailable)

I will map phase `0..9` to the current architecture and challenge requirements from [Hackathon - Third Round (1) 1.docx](/Users/work/Projects/Kifiya/Hackathon/Hackathon%20-%20Third%20Round%20%281%29%201.docx):

- Phase 0: Platform readiness and environment wiring
- Phase 1: Identity foundation and user lifecycle
- Phase 2: SSO/OIDC and session control
- Phase 3: eKYC orchestration and AI scoring
- Phase 4: Credential registration and card-linked identity
- Phase 5: Merchant transaction initiation and approval
- Phase 6: Admin observability and auditability
- Phase 7: Security hardening (JWT/DPoP/attestation/privacy)
- Phase 8: Testing, CI, and release quality gates
- Phase 9: Demo packaging and go/no-go readiness

## Evidence Collection Targets

- Auth/security/gateway: [auth-server](/Users/work/Projects/Kifiya/Hackathon/trust-layer/auth-server), [gateway](/Users/work/Projects/Kifiya/Hackathon/trust-layer/gateway)
- AI + eKYC: [ai-service](/Users/work/Projects/Kifiya/Hackathon/trust-layer/ai-service), [ekyc-service](/Users/work/Projects/Kifiya/Hackathon/trust-layer/ekyc-service)
- Tx + credential/card: [tx-service](/Users/work/Projects/Kifiya/Hackathon/trust-layer/tx-service), [vci-service](/Users/work/Projects/Kifiya/Hackathon/trust-layer/vci-service)
- Web and mobile: [frontend](/Users/work/Projects/Kifiya/Hackathon/trust-layer/frontend), [android-app](/Users/work/Projects/Kifiya/Hackathon/trust-layer/android-app)
- Runtime/ops: [deploy/docker-compose.yml](/Users/work/Projects/Kifiya/Hackathon/trust-layer/deploy/docker-compose.yml), module Dockerfiles

## Deliverable Assembly (exact requested format)

I will produce one structured audit report containing:

1. Executive summary with per-phase completion percentages and top 10 blockers.
2. Phase-by-phase audit table with status/evidence/gap/severity/fix/ETA.
3. Architecture conformance check (acceptable vs dangerous deviations).
4. API and flow validation matrix for 5 critical end-to-end flows.
5. Security and compliance audit with critical findings first.
6. Testing and quality audit with minimum pre-demo test gate.
7. Prioritized fix plan (P0/P1/P2) with exact files, acceptance criteria, and verification commands.
8. 48-hour recovery sprint with parallel streams and subagent ownership.
9. Binary go/no-go checklist with fallback options.

## Execution Strategy (when code changes are allowed)

- Apply the smallest safe P0 fixes first (runtime wiring, broken flow links, security miswirings).
- Validate each batch with service-level checks and targeted tests.
- Continue with P1 enhancements only after P0 pass criteria are met.

## Initial High-Confidence P0 Candidates (from current evidence)

- Fix Docker runtime AI URL wiring from eKYC to AI service in [ekyc-service config](/Users/work/Projects/Kifiya/Hackathon/trust-layer/ekyc-service/src/main/resources/application-docker.yml) and [docker-compose](/Users/work/Projects/Kifiya/Hackathon/trust-layer/deploy/docker-compose.yml).
- Correct false-positive eKYC fallback status behavior in [EkycVerificationService.java](/Users/work/Projects/Kifiya/Hackathon/trust-layer/ekyc-service/src/main/java/et/trustlayer/ekyc/service/EkycVerificationService.java).
- Wire DPoP enforcement into security filter chain in [SecurityConfig.java](/Users/work/Projects/Kifiya/Hackathon/trust-layer/auth-server/src/main/java/et/trustlayer/authserver/config/SecurityConfig.java).
- Fix front-end dev proxy/websocket/auth callback inconsistencies in [vite.config.ts](/Users/work/Projects/Kifiya/Hackathon/trust-layer/frontend/vite.config.ts), [OidcCallback.tsx](/Users/work/Projects/Kifiya/Hackathon/trust-layer/frontend/src/components/OidcCallback.tsx), and [RegistrationFlow.tsx](/Users/work/Projects/Kifiya/Hackathon/trust-layer/frontend/src/components/RegistrationFlow.tsx).
- Remove reliability blind spots by defining a minimum test gate in module build/test commands.

