import React, { useMemo, useState } from 'react';
import { Store, QrCode, History, BarChart3 } from 'lucide-react';
import axios from 'axios';
import { useAuth } from '../context/AuthContext';
import { useQuery } from '@tanstack/react-query';

type Tx = { txId: string; merchantId: string; status: string; amountMinor: number; currency: string; timestamp: string };

export const MerchantPortal: React.FC = () => {
  const { user } = useAuth();
  const [amountMinor, setAmountMinor] = useState(25000);
  const [nonce, setNonce] = useState<string | null>(null);

  const authHeaders = useMemo(
    () => ({ Authorization: `Bearer ${user?.access_token}` }),
    [user?.access_token],
  );
  const merchantId = user?.profile?.sub || 'merchant-demo';

  const { data: history = [], refetch } = useQuery<Tx[]>({
    queryKey: ['merchant-history', merchantId],
    queryFn: async () => (await axios.get(`/api/tx/merchant/${merchantId}/history`, { headers: authHeaders })).data,
  });

  const { data: reconciliation } = useQuery<{ approvedAmountMinor: number; approvedCount: number; currency: string }>({
    queryKey: ['merchant-reconciliation', merchantId],
    queryFn: async () => (await axios.get(`/api/tx/merchant/${merchantId}/reconciliation`, { headers: authHeaders })).data,
  });

  const initiatePayment = async () => {
    const response = await axios.post(
      '/api/tx/initiate',
      {
        userId: merchantId,
        merchantId,
        amountMinor,
        currency: 'ETB',
        description: 'Merchant initiated payment',
      },
      { headers: authHeaders },
    );
    setNonce(response.data.nonce ?? null);
    refetch();
  };

  return (
    <div className="w-full max-w-6xl mx-auto px-6 py-10 space-y-6">
      <div className="flex items-center gap-3">
        <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center text-primary">
          <Store className="w-6 h-6" />
        </div>
        <div>
          <h1 className="text-2xl font-heading font-black text-slate-800">Merchant Portal</h1>
          <p className="text-sm text-slate-500">Initiate payments, track settlement, and monitor approvals.</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <div className="glass-card p-5 rounded-xl border border-slate-100">
          <div className="text-xs uppercase tracking-widest text-slate-400 font-black mb-2 flex items-center gap-2"><BarChart3 className="w-4 h-4" />Reconciliation</div>
          <div className="text-2xl font-black text-slate-800">
            {(reconciliation?.approvedAmountMinor ?? 0) / 100} {reconciliation?.currency ?? 'ETB'}
          </div>
          <div className="text-xs text-slate-500 mt-1">{reconciliation?.approvedCount ?? 0} approved transactions</div>
        </div>

        <div className="glass-card p-5 rounded-xl border border-slate-100 lg:col-span-2">
          <div className="text-xs uppercase tracking-widest text-slate-400 font-black mb-3">Initiate Payment</div>
          <div className="flex items-center gap-3">
            <input
              type="number"
              className="border rounded-lg px-3 py-2 w-48"
              value={amountMinor}
              onChange={(e) => setAmountMinor(Number(e.target.value || 0))}
            />
            <button onClick={initiatePayment} className="btn btn-primary">Create Challenge</button>
          </div>
          {nonce && (
            <div className="mt-4 p-3 rounded-lg bg-primary/5 text-sm text-slate-700 flex items-center gap-2">
              <QrCode className="w-4 h-4 text-primary" />
              Nonce created: <span className="font-mono">{nonce}</span>
            </div>
          )}
        </div>
      </div>

      <div className="bg-white rounded-2xl border p-5">
        <div className="text-xs uppercase tracking-widest text-slate-400 font-black mb-3 flex items-center gap-2"><History className="w-4 h-4" />Transaction History</div>
        <div className="space-y-2">
          {history.map((tx) => (
            <div key={tx.txId} className="p-3 rounded-lg border border-slate-100 flex items-center justify-between">
              <div>
                <div className="font-semibold text-sm">{tx.merchantId}</div>
                <div className="text-xs text-slate-500">{new Date(tx.timestamp).toLocaleString()}</div>
              </div>
              <div className="text-right">
                <div className="font-mono font-bold text-sm">{tx.currency} {(tx.amountMinor / 100).toLocaleString()}</div>
                <div className="text-xs text-slate-500">{tx.status}</div>
              </div>
            </div>
          ))}
          {history.length === 0 && <div className="text-sm text-slate-500">No transactions yet.</div>}
        </div>
      </div>
    </div>
  );
};
