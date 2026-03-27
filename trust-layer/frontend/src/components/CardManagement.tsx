import React, { useMemo, useState } from 'react';
import axios from 'axios';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

type VirtualCard = {
  cardId: string;
  lastFour: string;
  cardNetwork: string;
  status: string;
  currency: string;
  dailyLimitMinor: number;
};

export const CardManagement: React.FC = () => {
  const { user } = useAuth();
  const { pushToast } = useToast();
  const userId = user?.profile?.sub || '';
  const headers = useMemo(() => ({ Authorization: `Bearer ${user?.access_token}` }), [user?.access_token]);
  const [selectedCard, setSelectedCard] = useState<string | null>(null);
  const [newDailyLimit, setNewDailyLimit] = useState(500000);

  const { data: cards = [], refetch, isLoading, isError } = useQuery<VirtualCard[]>({
    queryKey: ['cards-management', userId],
    enabled: !!userId,
    queryFn: async () => (await axios.get(`/api/vci/cards/${userId}`, { headers })).data,
  });

  const activeCard = cards.find((c) => c.cardId === selectedCard) || cards[0];

  const updateLimit = async () => {
    if (!activeCard) return;
    await axios.put(`/api/vci/cards/${activeCard.cardId}/limits`, { dailyLimitMinor: newDailyLimit }, { headers });
    pushToast('success', 'Card limit updated');
    refetch();
  };

  const toggleStatus = async () => {
    if (!activeCard) return;
    const next = activeCard.status === 'FROZEN' ? 'ACTIVE' : 'FROZEN';
    await axios.patch(`/api/vci/cards/${activeCard.cardId}/status`, { status: next }, { headers });
    pushToast('success', `Card set to ${next}`);
    refetch();
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-6 py-10 grid grid-cols-1 lg:grid-cols-3 gap-6">
      <section className="rounded-2xl border bg-white p-5 lg:col-span-1">
        <h1 className="text-2xl font-black">Card Management</h1>
        <div className="mt-4 space-y-2">
          {isLoading && <div className="text-sm text-slate-500">Loading cards...</div>}
          {isError && <div className="text-sm text-red-600">Failed to load cards.</div>}
          {!isLoading && cards.length === 0 && <div className="text-sm text-slate-500">No card provisioned yet.</div>}
          {cards.map((card) => (
            <button
              key={card.cardId}
              onClick={() => setSelectedCard(card.cardId)}
              className={`w-full rounded-lg border p-3 text-left ${activeCard?.cardId === card.cardId ? 'border-primary bg-primary/5' : 'border-slate-200'}`}
            >
              <div className="font-semibold">{card.cardNetwork}</div>
              <div className="text-xs text-slate-500">**** **** **** {card.lastFour}</div>
              <div className="text-xs text-slate-500">Status: {card.status}</div>
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-2xl border bg-white p-5 lg:col-span-2">
        {!activeCard && <div className="text-sm text-slate-500">Select a card to manage.</div>}
        {activeCard && (
          <div className="space-y-4">
            <h2 className="text-xl font-black">Card {activeCard.cardId.slice(0, 8)}...</h2>
            <div className="rounded-lg bg-slate-50 p-4 text-sm">
              <div>PAN: **** **** **** {activeCard.lastFour}</div>
              <div>Network: {activeCard.cardNetwork}</div>
              <div>Status: {activeCard.status}</div>
              <div>Daily limit: {activeCard.currency} {(activeCard.dailyLimitMinor / 100).toLocaleString()}</div>
            </div>

            <div className="flex items-center gap-3">
              <input
                type="number"
                value={newDailyLimit}
                onChange={(e) => setNewDailyLimit(Number(e.target.value || 0))}
                className="rounded-lg border px-3 py-2"
              />
              <button onClick={updateLimit} className="rounded-lg bg-primary px-4 py-2 text-sm font-bold text-white">
                Request Limit Update
              </button>
            </div>

            <button onClick={toggleStatus} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-bold text-white">
              {activeCard.status === 'FROZEN' ? 'Unfreeze Card' : 'Freeze Card'}
            </button>
          </div>
        )}
      </section>
    </div>
  );
};
