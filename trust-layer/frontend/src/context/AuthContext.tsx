import React, { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { UserManager, type UserManagerSettings, User } from 'oidc-client-ts';

interface AuthContextType {
  isAuthenticated: boolean;
  user: User | null;
  login: () => void;
  logout: () => void;
}

const settings: UserManagerSettings = {
  authority: 'http://localhost:9000',
  client_id: 'trust-layer-web',
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  response_type: 'code',
  scope: 'openid profile ekyc:verify tx:sign',
  automaticSilentRenew: true,
  monitorSession: true
};

const userManager = new UserManager(settings);

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    userManager.getUser().then(u => {
      if (u && !u.expired) {
        setUser(u);
      }
    });

    userManager.events.addUserLoaded((u) => setUser(u));
    userManager.events.addUserUnloaded(() => setUser(null));
  }, []);

  const login = () => userManager.signinRedirect();
  const logout = () => userManager.signoutRedirect();

  return (
    <AuthContext.Provider value={{ isAuthenticated: !!user, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
};
