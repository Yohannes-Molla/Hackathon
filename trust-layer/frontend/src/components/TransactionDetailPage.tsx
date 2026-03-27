import React from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';

type Tx = {
  txId: string;
  merchantId: string;
  status: string;
  amountMinor: number;
  currency: string;
  timestamp: string;
  riskScore?: number;
};

export const TransactionDetailPage: React.FC = () => {
  const { txId } = useParams();
  const location = useLocation();
  const tx = (location.state as { tx?: Tx } | null)?.tx;

  if (!tx) {
    return (
      <div className="mx-auto w-full max-w-4xl px-6 py-10">
        <h1 className="text-2xl font-black">Transaction Detail</h1>
        <p className="mt-2 text-sm text-slate-500">Transaction details for {txId} are not available in-memory.</p>
        <Link to="/transactions" className="mt-4 inline-block rounded-lg bg-primary px-4 py-2 text-sm font-bold text-white">
          Back to history
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-4xl px-6 py-10">
      <h1 className="text-2xl font-black">Transaction Detail</h1>
      <div className="mt-4 rounded-2xl border bg-white p-6 space-y-2">
        <div><span className="font-semibold">Tx ID:</span> {tx.txId}</div>
        <div><span className="font-semibold">Merchant:</span> {tx.merchantId}</div>
        <div><span className="font-semibold">Amount:</span> {tx.currency} {(tx.amountMinor / 100).toLocaleString()}</div>
        <div><span className="font-semibold">Status:</span> {tx.status}</div>
        <div><span className="font-semibold">Timestamp:</span> {new Date(tx.timestamp).toLocaleString()}</div>
        <div><span className="font-semibold">Fraud score:</span> {tx.riskScore ?? 'N/A'}</div>
      </div>
      <Link to="/transactions" className="mt-4 inline-block rounded-lg border px-4 py-2 text-sm font-bold">
        Back to history
      </Link>
    </div>
  );
};
