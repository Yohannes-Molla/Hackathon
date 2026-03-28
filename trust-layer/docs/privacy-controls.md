# Privacy Controls

## Data Minimization

- Only required identity attributes are stored in `user_identity`.
- Merchant and admin APIs expose only role-relevant fields.

## Masking

- Card display uses last four digits only.
- Sensitive credentials are not returned in standard API responses.

## Retention

- Session and nonce state held in Redis with strict TTL.
- Audit and transaction records retained for compliance review.

## User Controls

- Session listing and revocation endpoints available.
- Selective disclosure allows bounded claim sharing.

## Logging Policy

- Avoid logging raw document images and raw token content.
- Log IDs/statuses for observability without exposing PII.

