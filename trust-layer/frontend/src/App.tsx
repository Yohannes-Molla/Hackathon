import React, { useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantProvider, useTenant } from './context/TenantContext';
import { AuthProvider, useAuth } from './context/AuthContext';
import { RegistrationFlow } from './components/RegistrationFlow';
import { Dashboard } from './components/Dashboard';
import { AdminPortal } from './components/AdminPortal';
import { ShieldAlert } from 'lucide-react';

const queryClient = new QueryClient();

const Layout: React.FC = () => {
  const { tenant } = useTenant();
  const { isAuthenticated, logout } = useAuth();
  const [view, setView] = useState<'USER' | 'ADMIN'>('USER');

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <nav className="bg-white/95 backdrop-blur-md border-b sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3 cursor-pointer" onClick={() => setView('USER')}>
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
          </div>
          <div className="flex items-center gap-6">
             <div className="hidden md:flex items-center gap-4 text-xs font-black uppercase tracking-widest text-slate-400">
                 <button 
                    onClick={() => setView(view === 'ADMIN' ? 'USER' : 'ADMIN')} 
                    className={`flex items-center gap-1 transition-colors ${view === 'ADMIN' ? 'text-primary' : 'hover:text-primary'}`}
                 >
                    <ShieldAlert className="w-4 h-4" />
                    {view === 'ADMIN' ? 'Exit Admin' : 'Admin Portal'}
                 </button>
             </div>
             <button className="bg-slate-100 p-2 rounded-xl text-slate-900 border border-slate-200 hover:bg-slate-200 transition-colors">
                <span className="sr-only">Toggle Theme</span>
                🌙
              </button>
          </div>
        </div>
      </nav>

      <main className="flex-1 flex flex-col items-center justify-center relative">
        {view === 'ADMIN' ? (
          <AdminPortal />
        ) : (
          isAuthenticated ? (
            <Dashboard onLogout={logout} />
          ) : (
            <RegistrationFlow />
          )
        )}
      </main>

      <footer className="py-8 text-center text-[10px] font-black uppercase tracking-[0.2em] text-slate-400">
        <p>© 2026 The Trust Layer. All Rights Reserved. Federated Core Tier-1 Architecture.</p>
      </footer>
    </div>
  );
};


export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <TenantProvider>
          <Layout />
        </TenantProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
}
