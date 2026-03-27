import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import api from '../api/client';

type Tx = {
  txId: string;
  merchantId: string;
  status: string;
  amountMinor: number;
  currency: string;
  timestamp: string;
};

type PendingChallenge = {
  nonce: string;
  amountMinor: number;
  merchantId: string;
  currency: string;
  txId?: string;
};

const PAGE_SIZE = 10;

export const TransactionsPage: React.FC = () => {
  const { user } = useAuth();
  const { pushToast } = useToast();
  const queryClient = useQueryClient();
  const userId = user?.profile?.sub || '';
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [approving, setApproving] = useState<string | null>(null);

  const { data = [], isLoading, isError, refetch } = useQuery<Tx[]>({
    queryKey: ['tx-full-history', userId],
    enabled: !!userId,
    retry: 1,
    queryFn: async () => (await api.get(`/api/tx/history/${userId}`)).data,
  });

  const { data: pending = [], refetch: refetchPending } = useQuery<PendingChallenge[]>({
    queryKey: ['tx-pending-challenges', userId],
    enabled: !!user?.access_token,
    refetchInterval: 8000,
    retry: 1,
    queryFn: async () => (await api.get('/api/tx/pending-challenges')).data,
  });

  const approveChallenge = async (nonce: string) => {
    setApproving(nonce);
    try {
      const res = await api.post('/api/tx/approve-web', { nonce });
      pushToast('success', `Payment ${String(res.data.status ?? 'processed')}`);
      await queryClient.invalidateQueries({ queryKey: ['tx-full-history', userId] });
      await refetchPending();
    } catch (e: unknown) {
      const ax = e as { response?: { data?: { error?: string } } };
      pushToast('error', ax.response?.data?.error ?? 'Approval failed');
    } finally {
      setApproving(null);
    }
  };

  const filtered = data.filter((tx) => statusFilter === 'ALL' || tx.status === statusFilter);
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount);
  const current = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  return (
    <div className="mx-auto w-full max-w-6xl px-6 py-10">
      <h1 className="text-2xl font-black">Transaction History</h1>
      <div className="mt-4 flex items-center gap-3">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="rounded-lg border px-3 py-2 text-sm">
          <option value="ALL">All statuses</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
          <option value="PENDING_REVIEW">Pending review</option>
        </select>
        <button className="rounded-lg border px-3 py-2 text-sm" onClick={() => refetch()}>Refresh</button>
      </div>

      {isLoading && <div className="mt-4 text-sm text-slate-500">Loading transactions...</div>}
      {isError && (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-red-700 flex items-center justify-between gap-2">
          Failed to load transactions.
          <button type="button" className="text-sm underline" onClick={() => refetch()}>Retry</button>
        </div>
      )}

      {pending.length > 0 && (
        <div className="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-4">
          <h2 className="font-black text-sm text-amber-900">Pending payment challenges</h2>
          <p className="text-xs text-amber-800 mt-1">Approve with your signed-in account (web demo — no biometric signature).</p>
          <ul className="mt-3 space-y-2">
            {pending.map((p) => (
              <li key={p.nonce} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-white p-3 border border-amber-100">
                <div className="text-sm">
                  <span className="font-mono text-xs text-slate-500">{p.nonce.slice(0, 12)}…</span>
                  <div>
                    {p.currency} {(Number(p.amountMinor) / 100).toLocaleString()} — merchant {String(p.merchantId).slice(0, 8)}…
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn-primary text-sm py-1.5"
                  disabled={approving === p.nonce}
                  onClick={() => approveChallenge(p.nonce)}
                >
                  {approving === p.nonce ? 'Approving…' : 'Approve'}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="mt-4 space-y-2">
        {current.map((tx) => (
          <Link to={`/transactions/${tx.txId}`} state={{ tx }} key={tx.txId} className="block rounded-lg border bg-white p-3 hover:bg-slate-50">
            <div className="flex items-center justify-between">
              <div>
                <div className="font-semibold">{tx.merchantId}</div>
                <div className="text-xs text-slate-500">{new Date(tx.timestamp).toLocaleString()}</div>
              </div>
              <div className="text-right">
                <div className="font-mono text-sm">{tx.currency} {(tx.amountMinor / 100).toLocaleString()}</div>
                <div className="text-xs text-slate-500">{tx.status}</div>
              </div>
            </div>
          </Link>
        ))}
        {!isLoading && current.length === 0 && <div className="rounded-lg border bg-white p-3 text-sm text-slate-500">No transactions yet.</div>}
      </div>

      <div className="mt-4 flex items-center gap-2">
        <button disabled={safePage <= 1} onClick={() => setPage((p) => p - 1)} className="rounded border px-3 py-1 text-sm disabled:opacity-50">
          Previous
        </button>
        <span className="text-sm text-slate-500">Page {safePage} of {pageCount}</span>
        <button disabled={safePage >= pageCount} onClick={() => setPage((p) => p + 1)} className="rounded border px-3 py-1 text-sm disabled:opacity-50">
          Next
        </button>
      </div>
    </div>
  );
};
