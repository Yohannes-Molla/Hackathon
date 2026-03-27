/**
 * Generate a P-256 key pair in the browser and return the public JWK (JSON string) for /api/credentials/register.
 */
export async function generateSigningPublicJwkJson(): Promise<string> {
  const keyPair = await crypto.subtle.generateKey(
    { name: 'ECDSA', namedCurve: 'P-256' },
    true,
    ['sign', 'verify'],
  );
  const jwk = await crypto.subtle.exportKey('jwk', keyPair.publicKey);
  return JSON.stringify(jwk);
}

/** Stable demo payer Keycloak subject (matches deploy/keycloak/realm-trust-layer.json + Flyway V10). */
export const DEMO_PAYER_KEYCLOAK_SUB = 'd0000000-0000-4000-8000-000000000001';
