import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import api from '../api/client';
import { generateSigningPublicJwkJson } from '../utils/webCredential';

type VirtualCard = {
  cardId: string;
  lastFour: string;
  cardNetwork: string;
  status: string;
  currency: string;
  dailyLimitMinor: number;
};

type IdentityMe = { id: string; keycloakSub?: string; onboardingState?: string };

export const CardManagement: React.FC = () => {
  const { user } = useAuth();
  const { pushToast } = useToast();
  const queryClient = useQueryClient();
  const userId = user?.profile?.sub || '';
  const [selectedCard, setSelectedCard] = useState<string | null>(null);
  const [newDailyLimit, setNewDailyLimit] = useState(500000);
  const [credentialBusy, setCredentialBusy] = useState(false);

  const { data: identity } = useQuery<IdentityMe>({
    queryKey: ['identity-me'],
    enabled: !!user?.access_token,
    retry: 1,
    queryFn: async () => (await api.get('/api/identity/me')).data,
  });

  const { data: cards = [], refetch, isLoading, isError } = useQuery<VirtualCard[]>({
    queryKey: ['cards-management', userId],
    enabled: !!userId,
    retry: 1,
    queryFn: async () => (await api.get(`/api/vci/cards/${userId}`)).data,
  });

  const activeCard = cards.find((c) => c.cardId === selectedCard) || cards[0];

  const provisionCard = async () => {
    if (!userId) return;
    setCredentialBusy(true);
    try {
      const jwk = await generateSigningPublicJwkJson();
      const reg = await api.post<{ keyId: string }>(
        '/api/credentials/register',
        {
          jwk,
          attestationCertChain: 'web-demo-attestation',
          deviceId: `web-provision-${Date.now()}`,
        },
        { headers: { 'X-User-ID': identity?.id ?? '' } },
      );
      const keyId = reg.data.keyId;
      await api.post('/api/vci/provision', { userId, keyId });
      pushToast('success', 'Virtual card provisioned');
      await queryClient.invalidateQueries({ queryKey: ['cards-management', userId] });
      await queryClient.invalidateQueries({ queryKey: ['cards', userId] });
      await queryClient.invalidateQueries({ queryKey: ['identity-me'] });
      refetch();
    } catch (e: unknown) {
      const ax = e as { response?: { data?: { error?: string } } };
      pushToast('error', ax.response?.data?.error ?? 'Provisioning failed (complete eKYC first, or use a fresh credential).');
    } finally {
      setCredentialBusy(false);
    }
  };

  const updateLimit = async () => {
    if (!activeCard) return;
    try {
      await api.put(`/api/vci/cards/${activeCard.cardId}/limits`, { dailyLimitMinor: newDailyLimit });
      pushToast('success', 'Card limit updated');
      refetch();
    } catch {
      pushToast('error', 'Could not update limit');
    }
  };

  const toggleStatus = async () => {
    if (!activeCard) return;
    const next = activeCard.status === 'FROZEN' ? 'ACTIVE' : 'FROZEN';
    try {
      await api.patch(`/api/vci/cards/${activeCard.cardId}/status`, { status: next });
      pushToast('success', `Card set to ${next}`);
      refetch();
    } catch {
      pushToast('error', 'Could not change card status');
    }
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-6 py-10 grid grid-cols-1 lg:grid-cols-3 gap-6">
      <section className="rounded-2xl border bg-white p-5 lg:col-span-1">
        <h1 className="text-2xl font-black">Card Management</h1>
        <div className="mt-4 rounded-lg border border-slate-100 bg-slate-50 p-3 text-xs text-slate-600 space-y-2">
          <div>
            Onboarding: <span className="font-semibold">{identity?.onboardingState ?? '…'}</span>
          </div>
          <p>Provision binds a new browser-generated signing key (demo attestation). Complete eKYC on the Identity page first if provisioning fails.</p>
          <button
            type="button"
            className="btn btn-primary text-sm py-2 w-full"
            disabled={credentialBusy || !userId || !identity?.id}
            onClick={provisionCard}
          >
            Provision virtual card (web credential + bind)
          </button>
        </div>
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
