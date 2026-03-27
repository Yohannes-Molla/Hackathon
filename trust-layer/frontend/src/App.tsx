import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Routes, Route, Navigate, Link, Outlet, useLocation } from 'react-router-dom';
import { TenantProvider, useTenant } from './context/TenantContext';
import { AuthProvider, useAuth } from './context/AuthContext';
import { hasRealmRole } from './auth/keycloakRoles';
import { RegistrationFlow } from './components/RegistrationFlow';
import { Dashboard } from './components/Dashboard';
import { AdminPortal } from './components/AdminPortal';
import { MerchantPortal } from './components/MerchantPortal';
import { OidcCallback } from './components/OidcCallback';
import { ProtectedRoute } from './components/ProtectedRoute';
import { RoleRoute } from './components/RoleRoute';
import { ShieldAlert, Store } from 'lucide-react';

const queryClient = new QueryClient();

const HomePage: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="flex-1 flex items-center justify-center px-6">
        <div className="w-12 h-12 border-4 border-slate-200 border-t-primary rounded-full animate-spin" />
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return <RegistrationFlow />;
};

const MainLayout: React.FC = () => {
  const { tenant } = useTenant();
  const { isAuthenticated, logout, login, user } = useAuth();
  const location = useLocation();

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <nav className="bg-white/95 backdrop-blur-md border-b sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-3 cursor-pointer">
            <div className="w-9 h-9 rounded-xl bg-primary flex items-center justify-center text-white shadow-xl shadow-primary/20 font-bold group hover:rotate-6 transition-transform">
              🛡️
            </div>
            <div className="flex flex-col -space-y-1">
              <span className="text-xl font-heading font-black tracking-tight text-slate-800">
                {tenant?.name || 'The Trust Layer'}
              </span>
              <span className="text-[10px] font-black uppercase tracking-widest text-primary opacity-70">
                Federated Identity Hub
              </span>
            </div>
          </Link>
          <div className="flex items-center gap-4 md:gap-6">
            {isAuthenticated && (
              <div className="hidden md:flex items-center gap-4 text-xs font-black uppercase tracking-widest text-slate-400">
                <Link
                  to="/dashboard"
                  className={`transition-colors hover:text-primary ${location.pathname === '/dashboard' ? 'text-primary' : ''}`}
                >
                  Dashboard
                </Link>
                {hasRealmRole(user, 'admin') && (
                  <Link
                    to="/admin"
                    className={`flex items-center gap-1 transition-colors hover:text-primary ${location.pathname === '/admin' ? 'text-primary' : ''}`}
                  >
                    <ShieldAlert className="w-4 h-4" />
                    Admin
                  </Link>
                )}
                {hasRealmRole(user, 'merchant') && (
                  <Link
                    to="/merchant"
                    className={`flex items-center gap-1 transition-colors hover:text-primary ${location.pathname === '/merchant' ? 'text-primary' : ''}`}
                  >
                    <Store className="w-4 h-4" />
                    Merchant
                  </Link>
                )}
              </div>
            )}
            <div className="flex items-center gap-3">
              {!isAuthenticated && (
                <button
                  type="button"
                  onClick={() => login()}
                  className="text-xs font-black uppercase tracking-widest text-primary hover:text-primary/80 transition-colors"
                >
                  Sign in
                </button>
              )}
              {isAuthenticated && (
                <button
                  type="button"
                  onClick={() => logout()}
                  className="text-xs font-black uppercase tracking-widest text-slate-500 hover:text-primary transition-colors"
                >
                  Sign out
                </button>
              )}
              <button
                type="button"
                className="bg-slate-100 p-2 rounded-xl text-slate-900 border border-slate-200 hover:bg-slate-200 transition-colors"
              >
                <span className="sr-only">Toggle Theme</span>
                🌙
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="flex-1 flex flex-col items-stretch justify-start relative w-full">
        <Outlet />
      </main>

      <footer className="py-8 text-center text-[10px] font-black uppercase tracking-[0.2em] text-slate-400">
        <p>© 2026 The Trust Layer. All Rights Reserved. Federated Core Tier-1 Architecture.</p>
      </footer>
    </div>
  );
};

function AppRoutes() {
  return (
    <Routes>
      <Route path="/callback" element={<OidcCallback />} />
      <Route element={<MainLayout />}>
        <Route path="/" element={<HomePage />} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <RoleRoute role="admin">
              <AdminPortal />
            </RoleRoute>
          }
        />
        <Route
          path="/merchant"
          element={
            <RoleRoute role="merchant">
              <MerchantPortal />
            </RoleRoute>
          }
        />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <TenantProvider>
            <AppRoutes />
          </TenantProvider>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
