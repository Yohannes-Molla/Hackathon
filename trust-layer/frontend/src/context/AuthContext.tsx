import React, { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { UserManager, type UserManagerSettings, type User } from 'oidc-client-ts';

interface AuthContextType {
  isAuthenticated: boolean;
  isLoading: boolean;
  user: User | null;
  login: () => void;
  logout: () => void;
}

const authority = import.meta.env.VITE_OIDC_AUTHORITY || 'http://localhost:8180/realms/trust-layer';
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID || 'trust-layer-web';
// Standard OIDC scopes only; custom API scopes are assigned as default client scopes in Keycloak.
const scope = import.meta.env.VITE_OIDC_SCOPE || 'openid profile email';

const realmPath = '/realms/trust-layer/protocol/openid-connect';

const settings: UserManagerSettings = {
  authority,
  client_id: clientId,
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope,
  // Do not run silent renew (hidden iframe to Keycloak). It re-triggers on token expiry and looks
  // like a sign-in popup on every page, and fails when refresh_token is missing or silent URI matches /callback.
  automaticSilentRenew: false,
  // Session iframe (login-status-iframe) on every load; disable — looks like a popup / breaks with third-party cookies.
  monitorSession: false,
  // Route token/userinfo calls through the Vite dev-server proxy so the
  // browser makes a same-origin fetch and CORS is not required.
  metadataSeed: {
    // Same-origin + Vite/nginx proxy to Keycloak (avoids bad discovery URLs if KC is misconfigured)
    authorization_endpoint: `${window.location.origin}${realmPath}/auth`,
    end_session_endpoint: `${window.location.origin}${realmPath}/logout`,
    jwks_uri: `${window.location.origin}${realmPath}/certs`,
    token_endpoint: `${window.location.origin}${realmPath}/token`,
    userinfo_endpoint: `${window.location.origin}${realmPath}/userinfo`,
    revocation_endpoint: `${window.location.origin}${realmPath}/revoke`,
  },
};

export const userManager = new UserManager(settings);

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    userManager.getUser().then((u) => {
      if (u && !u.expired) {
        setUser(u);
      }
      setIsLoading(false);
    });

    userManager.events.addUserLoaded((u) => setUser(u));
    userManager.events.addUserUnloaded(() => setUser(null));
  }, []);

  const login = () => userManager.signinRedirect();
  const logout = () => userManager.signoutRedirect();

  return (
    <AuthContext.Provider value={{ isAuthenticated: !!user, isLoading, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
