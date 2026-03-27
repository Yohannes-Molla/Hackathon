import React, { useMemo, useState } from 'react';
import axios from 'axios';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Globe, Users, Activity, PlusCircle, ShieldAlert, BarChart3, FileText } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

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

type AdminTab = 'overview' | 'tenants' | 'users' | 'risk' | 'audit';

const TABS: { key: AdminTab; label: string; icon: React.ReactNode }[] = [
  { key: 'overview', label: 'Overview', icon: <Globe className="w-4 h-4" /> },
  { key: 'tenants', label: 'Tenants', icon: <ShieldAlert className="w-4 h-4" /> },
  { key: 'users', label: 'Users', icon: <Users className="w-4 h-4" /> },
  { key: 'risk', label: 'Risk', icon: <BarChart3 className="w-4 h-4" /> },
  { key: 'audit', label: 'Audit', icon: <FileText className="w-4 h-4" /> },
];

// ---------------------------------------------------------------------------
// Tenant creation form
// ---------------------------------------------------------------------------

const CreateTenantForm: React.FC<{ headers: Record<string, string>; onCreated: () => void }> = ({ headers, onCreated }) => {
  const { pushToast } = useToast();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [domain, setDomain] = useState('');
  const [primaryColor, setPrimaryColor] = useState('#6366f1');
  const [submitting, setSubmitting] = useState(false);

  const submit = async () => {
    if (!name.trim() || !slug.trim()) {
      pushToast('warning', 'Name and slug are required');
      return;
    }
    setSubmitting(true);
    try {
      await axios.post('/api/tenants', { name, slug, domain: domain || undefined, primaryColor }, { headers });
      pushToast('success', `Tenant "${name}" created`);
      setName(''); setSlug(''); setDomain(''); setPrimaryColor('#6366f1');
      setOpen(false);
      onCreated();
    } catch (err: unknown) {
      const msg = axios.isAxiosError(err) ? (err.response?.data as string) || err.message : 'Failed to create tenant';
      pushToast('error', msg);
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} className="flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-xs font-bold text-white">
        <PlusCircle className="w-4 h-4" /> New Tenant
      </button>
    );
  }

  return (
    <div className="rounded-xl border bg-white p-4 space-y-3 mt-3">
      <h3 className="font-black text-sm">Create Tenant</h3>
      <input placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} className="w-full rounded-lg border px-3 py-2 text-sm" />
      <input placeholder="Slug (e.g. bank-a)" value={slug} onChange={(e) => setSlug(e.target.value)} className="w-full rounded-lg border px-3 py-2 text-sm" />
      <input placeholder="Domain (optional)" value={domain} onChange={(e) => setDomain(e.target.value)} className="w-full rounded-lg border px-3 py-2 text-sm" />
      <div className="flex items-center gap-2">
        <label className="text-xs text-slate-500">Color</label>
        <input type="color" value={primaryColor} onChange={(e) => setPrimaryColor(e.target.value)} className="h-8 w-12 rounded border" />
      </div>
      <div className="flex gap-2">
        <button disabled={submitting} onClick={submit} className="rounded-lg bg-primary px-4 py-2 text-xs font-bold text-white disabled:opacity-50">
          {submitting ? 'Creating...' : 'Create'}
        </button>
        <button onClick={() => setOpen(false)} className="rounded-lg border px-4 py-2 text-xs font-bold">Cancel</button>
      </div>
    </div>
  );
};

// ---------------------------------------------------------------------------
// Shared display helpers
// ---------------------------------------------------------------------------

const LoadingRow = () => <div className="text-sm text-slate-500 py-3">Loading...</div>;

const ErrorRow: React.FC<{ retry: () => void }> = ({ retry }) => (
  <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
    Failed to load data. <button className="underline" onClick={retry}>Retry</button>
  </div>
);

const EmptyRow: React.FC<{ text: string }> = ({ text }) => (
  <div className="text-sm text-slate-500 py-3">{text}</div>
);

// ---------------------------------------------------------------------------
// Main admin portal
// ---------------------------------------------------------------------------

export const AdminPortal: React.FC = () => {
  const { user } = useAuth();
  const { pushToast } = useToast();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<AdminTab>('overview');
  const [selectedTenant, setSelectedTenant] = useState<string | null>(null);
  const [selectedUser, setSelectedUser] = useState<string | null>(null);

  // Audit log client-side filters
  const [auditActionFilter, setAuditActionFilter] = useState('');
  const [auditActorFilter, setAuditActorFilter] = useState('');
  const [auditDateFrom, setAuditDateFrom] = useState('');
  const [auditDateTo, setAuditDateTo] = useState('');

  const authHeaders = useMemo(
    () => ({ Authorization: `Bearer ${user?.access_token}` }),
    [user?.access_token],
  );

  // ----- queries -----
  const tenantsQ = useQuery<Tenant[]>({
    queryKey: ['admin-tenants'],
    queryFn: async () => (await axios.get('/api/admin/tenants', { headers: authHeaders })).data,
  });

  const usersQ = useQuery<UserIdentity[]>({
    queryKey: ['admin-users', selectedTenant],
    enabled: !!selectedTenant,
    queryFn: async () => (await axios.get(`/api/admin/tenants/${selectedTenant}/users`, { headers: authHeaders })).data,
  });

  const transactionsQ = useQuery<Tx[]>({
    queryKey: ['admin-user-tx', selectedUser],
    enabled: !!selectedUser,
    queryFn: async () => (await axios.get(`/api/admin/users/${selectedUser}/transactions`, { headers: authHeaders })).data,
  });

  const riskQ = useQuery<RiskSummary>({
    queryKey: ['admin-risk-summary'],
    queryFn: async () => (await axios.get('/api/admin/risk-summary', { headers: authHeaders })).data,
  });

  const auditQ = useQuery<AuditLogEntry[]>({
    queryKey: ['admin-audit-logs'],
    queryFn: async () => (await axios.get('/api/admin/audit-logs', { headers: authHeaders })).data,
  });

  const tenants = tenantsQ.data ?? [];
  const users = usersQ.data ?? [];
  const transactions = transactionsQ.data ?? [];
  const riskSummary = riskQ.data;
  const auditLogs = auditQ.data ?? [];

  const selectedUserObj = users.find((u) => u.id === selectedUser);

  // Flagged (high-risk) users across loaded tenant users
  const flaggedUsers = users.filter((u) => u.riskScore != null && u.riskScore >= 70);

  // Client-side audit log filter
  const filteredAuditLogs = auditLogs.filter((log) => {
    if (auditActionFilter && !log.action.toLowerCase().includes(auditActionFilter.toLowerCase())) return false;
    if (auditActorFilter && !(log.actor || '').toLowerCase().includes(auditActorFilter.toLowerCase())) return false;
    if (auditDateFrom && log.createdAt < auditDateFrom) return false;
    if (auditDateTo && log.createdAt > auditDateTo + 'T23:59:59') return false;
    return true;
  });

  // Risk bar widths
  const riskTotal = riskSummary ? Math.max(riskSummary.totalUsers, 1) : 1;

  // ----- mutations -----
  const updateUserStatus = async (userId: string, status: string) => {
    if (!window.confirm(`Set user status to "${status}"?`)) return;
    try {
      await axios.patch(`/api/admin/users/${userId}/status`, { status }, { headers: authHeaders });
      pushToast('success', `User status updated to ${status}`);
      queryClient.invalidateQueries({ queryKey: ['admin-users', selectedTenant] });
    } catch {
      pushToast('error', 'Failed to update user status');
    }
  };

  // ======================= RENDER =======================

  return (
    <div className="max-w-7xl mx-auto w-full px-6 py-8">
      {/* Header */}
      <header className="mb-6">
        <h1 className="text-3xl font-heading font-black flex items-center gap-3">
          <Globe className="text-primary w-8 h-8" />
          Admin Portal
        </h1>
        <p className="text-slate-500 mt-1">Live tenant, user, and transaction monitoring.</p>
      </header>

      {/* Tab bar */}
      <div className="flex gap-1 overflow-x-auto border-b mb-6">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-2 px-4 py-2 text-xs font-black uppercase tracking-widest transition-colors whitespace-nowrap ${
              activeTab === tab.key ? 'border-b-2 border-primary text-primary' : 'text-slate-400 hover:text-slate-600'
            }`}
          >
            {tab.icon} {tab.label}
          </button>
        ))}
      </div>

      {/* =================== OVERVIEW TAB =================== */}
      {activeTab === 'overview' && (
        <div className="space-y-6">
          {/* Risk summary cards */}
          <section className="bg-white rounded-2xl border p-4">
            <h2 className="font-black mb-3 flex items-center gap-2"><BarChart3 className="w-4 h-4" /> Risk Overview</h2>
            {riskQ.isLoading && <LoadingRow />}
            {riskQ.isError && <ErrorRow retry={() => riskQ.refetch()} />}
            {riskSummary && (
              <>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                  <div className="rounded-lg border border-red-200 bg-red-50 p-3">
                    <div className="text-slate-600">High</div>
                    <div className="text-lg font-black text-red-700">{riskSummary.highRisk}</div>
                  </div>
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3">
                    <div className="text-slate-600">Medium</div>
                    <div className="text-lg font-black text-amber-700">{riskSummary.mediumRisk}</div>
                  </div>
                  <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3">
                    <div className="text-slate-600">Low</div>
                    <div className="text-lg font-black text-emerald-700">{riskSummary.lowRisk}</div>
                  </div>
                  <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                    <div className="text-slate-600">Total</div>
                    <div className="text-lg font-black text-slate-700">{riskSummary.totalUsers}</div>
                  </div>
                </div>
                {/* Bar chart */}
                <div className="mt-4 space-y-2">
                  <div className="flex items-center gap-2 text-xs">
                    <span className="w-16 text-slate-500">High</span>
                    <div className="flex-1 h-4 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-red-500 rounded-full" style={{ width: `${(riskSummary.highRisk / riskTotal) * 100}%` }} />
                    </div>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <span className="w-16 text-slate-500">Medium</span>
                    <div className="flex-1 h-4 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-amber-500 rounded-full" style={{ width: `${(riskSummary.mediumRisk / riskTotal) * 100}%` }} />
                    </div>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <span className="w-16 text-slate-500">Low</span>
                    <div className="flex-1 h-4 bg-slate-100 rounded-full overflow-hidden">
                      <div className="h-full bg-emerald-500 rounded-full" style={{ width: `${(riskSummary.lowRisk / riskTotal) * 100}%` }} />
                    </div>
                  </div>
                </div>
              </>
            )}
          </section>

          {/* Quick stats row */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-white rounded-2xl border p-4">
              <div className="text-xs uppercase tracking-widest text-slate-400 font-black">Tenants</div>
              <div className="text-2xl font-black mt-1">{tenants.length}</div>
            </div>
            <div className="bg-white rounded-2xl border p-4">
              <div className="text-xs uppercase tracking-widest text-slate-400 font-black">Audit Events</div>
              <div className="text-2xl font-black mt-1">{auditLogs.length}</div>
            </div>
            <div className="bg-white rounded-2xl border p-4">
              <div className="text-xs uppercase tracking-widest text-slate-400 font-black">High-Risk Users</div>
              <div className="text-2xl font-black mt-1 text-red-700">{riskSummary?.highRisk ?? 0}</div>
            </div>
          </div>
        </div>
      )}

      {/* =================== TENANTS TAB =================== */}
      {activeTab === 'tenants' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="font-black text-lg flex items-center gap-2"><ShieldAlert className="w-5 h-5" /> Tenants</h2>
          </div>
          <CreateTenantForm headers={authHeaders} onCreated={() => tenantsQ.refetch()} />

          {tenantsQ.isLoading && <LoadingRow />}
          {tenantsQ.isError && <ErrorRow retry={() => tenantsQ.refetch()} />}
          {!tenantsQ.isLoading && tenants.length === 0 && <EmptyRow text="No tenants found." />}

          <div className="space-y-2">
            {tenants.map((tenant) => (
              <button
                key={tenant.slug}
                onClick={() => { setSelectedTenant(tenant.slug); setSelectedUser(null); setActiveTab('users'); }}
                className="w-full text-left p-4 rounded-xl border bg-white hover:border-primary/40 transition-colors"
              >
                <div className="font-semibold">{tenant.name}</div>
                <div className="text-xs text-slate-500">{tenant.slug}{tenant.domain ? ` • ${tenant.domain}` : ''}</div>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* =================== USERS TAB =================== */}
      {activeTab === 'users' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Left: tenant selector + user list */}
          <div className="space-y-4">
            <div className="flex items-center gap-3 flex-wrap">
              <h2 className="font-black text-lg flex items-center gap-2"><Users className="w-5 h-5" /> Users</h2>
              <select
                value={selectedTenant || ''}
                onChange={(e) => { setSelectedTenant(e.target.value || null); setSelectedUser(null); }}
                className="rounded-lg border px-3 py-2 text-sm"
              >
                <option value="">Select tenant...</option>
                {tenants.map((t) => <option key={t.slug} value={t.slug}>{t.name}</option>)}
              </select>
            </div>

            {!selectedTenant && <EmptyRow text="Select a tenant to view its users." />}
            {selectedTenant && usersQ.isLoading && <LoadingRow />}
            {selectedTenant && usersQ.isError && <ErrorRow retry={() => usersQ.refetch()} />}
            {selectedTenant && !usersQ.isLoading && users.length === 0 && <EmptyRow text="No users in this tenant." />}

            <div className="space-y-2">
              {users.map((entry) => (
                <button
                  key={entry.id}
                  onClick={() => setSelectedUser(entry.id)}
                  className={`w-full text-left p-3 rounded-lg border transition-colors ${selectedUser === entry.id ? 'border-primary bg-primary/5' : 'border-slate-200 bg-white hover:border-slate-300'}`}
                >
                  <div className="font-semibold text-sm">{entry.givenName} {entry.familyName}</div>
                  <div className="text-xs text-slate-500">
                    {entry.onboardingState || '-'} &bull; {entry.assuranceLevel || '-'} &bull; risk {entry.riskScore ?? '-'}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Right: user detail drill-down */}
          <div>
            {!selectedUser && <div className="rounded-2xl border bg-white p-6 text-sm text-slate-500">Select a user to view details.</div>}
            {selectedUser && selectedUserObj && (
              <div className="rounded-2xl border bg-white p-6 space-y-5">
                <h3 className="text-lg font-black">{selectedUserObj.givenName} {selectedUserObj.familyName}</h3>

                {/* Identity detail panel */}
                <div className="rounded-lg bg-slate-50 p-4 text-sm space-y-1">
                  <div><span className="font-semibold">User ID:</span> {selectedUserObj.id}</div>
                  <div><span className="font-semibold">Keycloak Sub:</span> {selectedUserObj.keycloakSub || '-'}</div>
                  <div><span className="font-semibold">Onboarding:</span> {selectedUserObj.onboardingState || '-'}</div>
                  <div><span className="font-semibold">Assurance:</span> {selectedUserObj.assuranceLevel || '-'}</div>
                  <div>
                    <span className="font-semibold">Risk Score:</span>{' '}
                    <span className={
                      selectedUserObj.riskScore != null && selectedUserObj.riskScore >= 70 ? 'text-red-700 font-bold' :
                      selectedUserObj.riskScore != null && selectedUserObj.riskScore >= 40 ? 'text-amber-700 font-bold' :
                      'text-emerald-700'
                    }>
                      {selectedUserObj.riskScore ?? 'N/A'}
                    </span>
                  </div>
                </div>

                {/* Status management */}
                <div className="space-y-2">
                  <h4 className="font-black text-sm">Status Management</h4>
                  <div className="flex flex-wrap gap-2">
                    {['ACTIVE', 'SUSPENDED', 'EKYC_PENDING', 'EKYC_COMPLETE'].map((status) => (
                      <button
                        key={status}
                        disabled={selectedUserObj.onboardingState === status}
                        onClick={() => updateUserStatus(selectedUserObj.id, status)}
                        className={`rounded-lg px-3 py-1.5 text-xs font-bold transition-colors ${
                          selectedUserObj.onboardingState === status
                            ? 'bg-primary/10 text-primary border border-primary/30'
                            : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                        }`}
                      >
                        {status}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Transaction history */}
                <div className="space-y-2">
                  <h4 className="font-black text-sm flex items-center gap-2"><Activity className="w-4 h-4" /> Transactions</h4>
                  {transactionsQ.isLoading && <LoadingRow />}
                  {transactionsQ.isError && <ErrorRow retry={() => transactionsQ.refetch()} />}
                  {!transactionsQ.isLoading && transactions.length === 0 && <EmptyRow text="No transactions for this user." />}
                  <div className="space-y-2 max-h-72 overflow-auto">
                    {transactions.map((tx) => (
                      <div key={tx.txId} className="p-3 rounded-lg border border-slate-200 text-sm">
                        <div className="flex justify-between">
                          <span className="font-semibold">{tx.merchantId}</span>
                          <span className="font-mono">{tx.currency} {(tx.amountMinor / 100).toLocaleString()}</span>
                        </div>
                        <div className="text-xs text-slate-500">{tx.status} &bull; {new Date(tx.timestamp).toLocaleString()}</div>
                      </div>
                    ))}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* =================== RISK TAB =================== */}
      {activeTab === 'risk' && (
        <div className="space-y-6">
          <h2 className="font-black text-lg flex items-center gap-2"><BarChart3 className="w-5 h-5" /> Risk Scoring Dashboard</h2>

          {riskQ.isLoading && <LoadingRow />}
          {riskQ.isError && <ErrorRow retry={() => riskQ.refetch()} />}

          {riskSummary && (
            <>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                <div className="rounded-lg border border-red-200 bg-red-50 p-3">
                  <div className="text-slate-600">High (&ge;70)</div>
                  <div className="text-lg font-black text-red-700">{riskSummary.highRisk}</div>
                </div>
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-3">
                  <div className="text-slate-600">Medium (40-69)</div>
                  <div className="text-lg font-black text-amber-700">{riskSummary.mediumRisk}</div>
                </div>
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3">
                  <div className="text-slate-600">Low (&lt;40)</div>
                  <div className="text-lg font-black text-emerald-700">{riskSummary.lowRisk}</div>
                </div>
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                  <div className="text-slate-600">Total</div>
                  <div className="text-lg font-black text-slate-700">{riskSummary.totalUsers}</div>
                </div>
              </div>

              {/* Distribution bar */}
              <div className="rounded-2xl border bg-white p-4 space-y-3">
                <h3 className="font-black text-sm">Distribution</h3>
                <div className="space-y-2">
                  {[
                    { label: 'High', value: riskSummary.highRisk, color: 'bg-red-500' },
                    { label: 'Medium', value: riskSummary.mediumRisk, color: 'bg-amber-500' },
                    { label: 'Low', value: riskSummary.lowRisk, color: 'bg-emerald-500' },
                  ].map((row) => (
                    <div key={row.label} className="flex items-center gap-3 text-sm">
                      <span className="w-16 text-slate-500">{row.label}</span>
                      <div className="flex-1 h-5 bg-slate-100 rounded-full overflow-hidden">
                        <div className={`h-full rounded-full ${row.color}`} style={{ width: `${(row.value / riskTotal) * 100}%` }} />
                      </div>
                      <span className="w-10 text-right font-mono text-xs">{row.value}</span>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}

          {/* Flagged users */}
          <div className="rounded-2xl border bg-white p-4">
            <h3 className="font-black text-sm mb-3">Flagged Users (risk &ge; 70)</h3>
            {!selectedTenant && <div className="text-sm text-slate-500">Select a tenant in the Users tab to see flagged users.</div>}
            {selectedTenant && usersQ.isLoading && <LoadingRow />}
            {selectedTenant && !usersQ.isLoading && flaggedUsers.length === 0 && <EmptyRow text="No high-risk users in this tenant." />}
            <div className="space-y-2">
              {flaggedUsers.map((u) => (
                <div key={u.id} className="p-3 rounded-lg border border-red-200 bg-red-50 flex justify-between items-center text-sm">
                  <div>
                    <span className="font-semibold">{u.givenName} {u.familyName}</span>
                    <span className="text-xs text-slate-500 ml-2">{u.onboardingState}</span>
                  </div>
                  <span className="font-mono font-bold text-red-700">Score: {u.riskScore}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* =================== AUDIT TAB =================== */}
      {activeTab === 'audit' && (
        <div className="space-y-4">
          <h2 className="font-black text-lg flex items-center gap-2"><FileText className="w-5 h-5" /> Audit Logs</h2>

          {/* Filters */}
          <div className="flex flex-wrap gap-3 items-end">
            <div>
              <label className="text-xs text-slate-500 block mb-1">Action</label>
              <input
                placeholder="e.g. CREDENTIAL_REGISTER"
                value={auditActionFilter}
                onChange={(e) => setAuditActionFilter(e.target.value)}
                className="rounded-lg border px-3 py-2 text-sm w-52"
              />
            </div>
            <div>
              <label className="text-xs text-slate-500 block mb-1">Actor</label>
              <input
                placeholder="e.g. admin"
                value={auditActorFilter}
                onChange={(e) => setAuditActorFilter(e.target.value)}
                className="rounded-lg border px-3 py-2 text-sm w-40"
              />
            </div>
            <div>
              <label className="text-xs text-slate-500 block mb-1">From</label>
              <input type="date" value={auditDateFrom} onChange={(e) => setAuditDateFrom(e.target.value)} className="rounded-lg border px-3 py-2 text-sm" />
            </div>
            <div>
              <label className="text-xs text-slate-500 block mb-1">To</label>
              <input type="date" value={auditDateTo} onChange={(e) => setAuditDateTo(e.target.value)} className="rounded-lg border px-3 py-2 text-sm" />
            </div>
            <button onClick={() => { setAuditActionFilter(''); setAuditActorFilter(''); setAuditDateFrom(''); setAuditDateTo(''); }} className="rounded-lg border px-3 py-2 text-xs font-bold">
              Clear
            </button>
          </div>

          <div className="text-xs text-slate-500">{filteredAuditLogs.length} of {auditLogs.length} entries shown</div>

          {auditQ.isLoading && <LoadingRow />}
          {auditQ.isError && <ErrorRow retry={() => auditQ.refetch()} />}
          {!auditQ.isLoading && filteredAuditLogs.length === 0 && <EmptyRow text="No audit log entries match your filters." />}

          <div className="space-y-2 max-h-[32rem] overflow-auto">
            {filteredAuditLogs.map((log) => (
              <div key={log.id} className="p-3 rounded-lg border border-slate-200 bg-white text-sm">
                <div className="flex justify-between">
                  <span className="font-semibold">{log.action}</span>
                  <span className="text-xs text-slate-500">{new Date(log.createdAt).toLocaleString()}</span>
                </div>
                <div className="text-xs text-slate-500">
                  by {log.actor || 'system'} &bull; {log.resourceType}:{log.resourceId} &bull; IP {log.ipAddress || '-'}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
