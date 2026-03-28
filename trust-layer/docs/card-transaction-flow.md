# Card Transaction Flow

## Provisioning

1. User completes eKYC.
2. Client registers credential (`/api/credentials/register`).
3. Card is provisioned (`/api/vci/provision`).
4. PAN token is stored as Vault ciphertext or fallback token.

## Transaction

1. Merchant initiates challenge (`/api/tx/initiate`).
2. User receives push/deep-link, approves with biometric.
3. Signed payload is submitted (`/api/tx/submit`).
4. `tx-service` verifies signature, nonce, card state, and fraud score.
5. Transaction is persisted to `transactions`.
6. Event is published for portal synchronization.

## Reconciliation

- Merchant history: `/api/tx/merchant/{merchantId}/history`
- Merchant summary: `/api/tx/merchant/{merchantId}/reconciliation`

