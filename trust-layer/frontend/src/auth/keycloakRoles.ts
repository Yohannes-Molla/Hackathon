import type { User } from 'oidc-client-ts';

function parseJwtPayload(accessToken: string): Record<string, unknown> {
  const parts = accessToken.split('.');
  if (parts.length < 2) return {};
  const payload = parts[1];
  const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4);
  const base64 = padded.replace(/-/g, '+').replace(/_/g, '/');
  const json = atob(base64);
  return JSON.parse(json) as Record<string, unknown>;
}

/** Realm roles from Keycloak access token (`realm_access.roles`). */
export function getRealmRolesFromAccessToken(accessToken: string | undefined): string[] {
  if (!accessToken) return [];
  try {
    const payload = parseJwtPayload(accessToken);
    const realmAccess = payload.realm_access as { roles?: string[] } | undefined;
    const roles = realmAccess?.roles;
    return Array.isArray(roles) ? roles : [];
  } catch {
    return [];
  }
}

export function getRealmRoles(user: User | null): string[] {
  if (!user) return [];
  const fromAccess = getRealmRolesFromAccessToken(user.access_token);
  if (fromAccess.length > 0) return fromAccess;
  const profile = user.profile as Record<string, unknown> | undefined;
  const realmRoles = profile?.realm_roles;
  if (Array.isArray(realmRoles)) return realmRoles as string[];
  return [];
}

export function hasRealmRole(user: User | null, role: string): boolean {
  return getRealmRoles(user).includes(role);
}

export function isAdminOrMerchant(user: User | null): boolean {
  return hasRealmRole(user, 'admin') || hasRealmRole(user, 'merchant');
}

export function getDefaultRouteForUser(user: User | null): string {
  if (hasRealmRole(user, 'admin')) return '/admin';
  if (hasRealmRole(user, 'merchant')) return '/merchant';
  return '/dashboard';
}
