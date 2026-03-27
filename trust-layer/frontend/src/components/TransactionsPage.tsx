import React, { useMemo, useState } from 'react';
import axios from 'axios';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

type Tx = {
  txId: string;
  merchantId: string;
  status: string;
  amountMinor: number;
  currency: string;
  timestamp: string;
};

const PAGE_SIZE = 10;

export const TransactionsPage: React.FC = () => {
  const { user } = useAuth();
  const userId = user?.profile?.sub || '';
  const headers = useMemo(() => ({ Authorization: `Bearer ${user?.access_token}` }), [user?.access_token]);
  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState('ALL');

  const { data = [], isLoading, isError, refetch } = useQuery<Tx[]>({
    queryKey: ['tx-full-history', userId],
    enabled: !!userId,
    queryFn: async () => (await axios.get(`/api/tx/history/${userId}`, { headers })).data,
  });

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
          <option value="PENDING">Pending</option>
        </select>
        <button className="rounded-lg border px-3 py-2 text-sm" onClick={() => refetch()}>Refresh</button>
      </div>

      {isLoading && <div className="mt-4 text-sm text-slate-500">Loading transactions...</div>}
      {isError && <div className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-red-700">Failed to load transactions.</div>}

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
