import React, { useMemo, useState } from 'react';
import axios from 'axios';
import { useQuery } from '@tanstack/react-query';
import { Globe, Users, Activity } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

type Tenant = { id?: string; slug: string; name: string; domain?: string };
type Tx = { txId: string; merchantId: string; status: string; amountMinor: number; currency: string; timestamp: string };
type AuditLogEntry = {
  id: number;
  actor: string;
  action: string;
  resourceType: string;
  resourceId: string;
  ipAddress: string;
  createdAt: string;
};
type RiskSummary = {
  highRisk: number;
  mediumRisk: number;
  lowRisk: number;
  totalUsers: number;
};
type UserIdentity = {
  id: string;
  keycloakSub?: string;
  givenName?: string;
  familyName?: string;
  onboardingState?: string;
  assuranceLevel?: string;
  riskScore?: number | null;
};

export const AdminPortal: React.FC = () => {
  const { user } = useAuth();
  const [selectedTenant, setSelectedTenant] = useState<string | null>(null);
  const [selectedUser, setSelectedUser] = useState<string | null>(null);

  const authHeaders = useMemo(
    () => ({ Authorization: `Bearer ${user?.access_token}` }),
    [user?.access_token],
  );

  const { data: tenants = [] } = useQuery<Tenant[]>({
    queryKey: ['admin-tenants'],
    queryFn: async () => (await axios.get('/api/admin/tenants', { headers: authHeaders })).data,
  });

  const { data: users = [] } = useQuery<UserIdentity[]>({
    queryKey: ['admin-users', selectedTenant],
    enabled: !!selectedTenant,
    queryFn: async () => (await axios.get('/api/admin/tenants/' + selectedTenant + '/users', { headers: authHeaders })).data,
  });

  const { data: transactions = [] } = useQuery<Tx[]>({
    queryKey: ['admin-user-tx', selectedUser],
    enabled: !!selectedUser,
    queryFn: async () => (await axios.get('/api/admin/users/' + selectedUser + '/transactions', { headers: authHeaders })).data,
  });

  const { data: riskSummary } = useQuery<RiskSummary>({
    queryKey: ['admin-risk-summary'],
    queryFn: async () => (await axios.get('/api/admin/risk-summary', { headers: authHeaders })).data,
  });

  const { data: auditLogs = [] } = useQuery<AuditLogEntry[]>({
    queryKey: ['admin-audit-logs'],
    queryFn: async () => (await axios.get('/api/admin/audit-logs', { headers: authHeaders })).data,
  });

  return (
    <div className="max-w-7xl mx-auto w-full px-6 py-8">
      <header className="mb-8">
        <h1 className="text-3xl font-heading font-black flex items-center gap-3">
          <Globe className="text-primary w-8 h-8" />
          Admin Portal
        </h1>
        <p className="text-slate-500 mt-1">Live tenant, user, and transaction monitoring.</p>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <section className="bg-white rounded-2xl border p-4">
          <h2 className="font-black mb-3 flex items-center gap-2"><Users className="w-4 h-4" /> Tenants</h2>
          <div className="space-y-2">
            {tenants.map((tenant) => (
              <button
                key={tenant.slug}
                onClick={() => { setSelectedTenant(tenant.slug); setSelectedUser(null); }}
                className={`w-full text-left p-3 rounded-lg border ${selectedTenant === tenant.slug ? 'border-primary bg-primary/5' : 'border-slate-200'}`}
              >
                <div className="font-semibold text-sm">{tenant.name}</div>
                <div className="text-xs text-slate-500">{tenant.slug}</div>
              </button>
            ))}
          </div>
        </section>

        <section className="bg-white rounded-2xl border p-4">
          <h2 className="font-black mb-3 flex items-center gap-2"><Users className="w-4 h-4" /> Users</h2>
          <div className="space-y-2">
            {users.map((entry) => (
              <button
                key={entry.id}
                onClick={() => setSelectedUser(entry.id)}
                className={`w-full text-left p-3 rounded-lg border ${selectedUser === entry.id ? 'border-primary bg-primary/5' : 'border-slate-200'}`}
              >
                <div className="font-semibold text-sm">{entry.givenName} {entry.familyName}</div>
                <div className="text-xs text-slate-500">{entry.onboardingState} • {entry.assuranceLevel} • risk {entry.riskScore ?? '-'}</div>
              </button>
            ))}
          </div>
        </section>

        <section className="bg-white rounded-2xl border p-4">
          <h2 className="font-black mb-3 flex items-center gap-2"><Activity className="w-4 h-4" /> Transactions</h2>
          <div className="space-y-2">
            {transactions.map((tx) => (
              <div key={tx.txId} className="p-3 rounded-lg border border-slate-200">
                <div className="font-semibold text-sm">{tx.merchantId}</div>
                <div className="text-xs text-slate-500">{tx.status} • {tx.currency} {(tx.amountMinor / 100).toLocaleString()}</div>
              </div>
            ))}
          </div>
        </section>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-6">
        <section className="bg-white rounded-2xl border p-4">
          <h2 className="font-black mb-3 flex items-center gap-2"><Activity className="w-4 h-4" /> Risk Summary</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
            <div className="rounded-lg border border-red-200 bg-red-50 p-3">
              <div className="text-slate-600">High</div>
              <div className="text-lg font-black text-red-700">{riskSummary?.highRisk ?? 0}</div>
            </div>
            <div className="rounded-lg border border-amber-200 bg-amber-50 p-3">
              <div className="text-slate-600">Medium</div>
              <div className="text-lg font-black text-amber-700">{riskSummary?.mediumRisk ?? 0}</div>
            </div>
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3">
              <div className="text-slate-600">Low</div>
              <div className="text-lg font-black text-emerald-700">{riskSummary?.lowRisk ?? 0}</div>
            </div>
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <div className="text-slate-600">Total</div>
              <div className="text-lg font-black text-slate-700">{riskSummary?.totalUsers ?? 0}</div>
            </div>
          </div>
        </section>

        <section className="bg-white rounded-2xl border p-4">
          <h2 className="font-black mb-3 flex items-center gap-2"><Activity className="w-4 h-4" /> Audit Logs</h2>
          <div className="space-y-2 max-h-72 overflow-auto">
            {auditLogs.map((log) => (
              <div key={log.id} className="p-3 rounded-lg border border-slate-200">
                <div className="font-semibold text-sm">{log.action} <span className="text-slate-500">by {log.actor || 'system'}</span></div>
                <div className="text-xs text-slate-500">
                  {log.resourceType}:{log.resourceId} • {log.ipAddress || '-'} • {new Date(log.createdAt).toLocaleString()}
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
};
