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
const scope = import.meta.env.VITE_OIDC_SCOPE || 'openid profile email offline_access ekyc:verify vci:provision tx:sign identity:read';

const settings: UserManagerSettings = {
  authority,
  client_id: clientId,
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope,
  automaticSilentRenew: true,
  monitorSession: true
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
