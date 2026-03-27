import React, { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import api from '../api/client';

type IdentityMeResponse = {
  id: string;
  keycloakSub?: string;
  givenName?: string;
  familyName?: string;
  dateOfBirth?: string;
  nationality?: string;
  assuranceLevel?: string;
  onboardingState?: string;
  riskScore?: number;
};

const fields: Array<keyof IdentityMeResponse> = ['givenName', 'familyName', 'dateOfBirth', 'nationality', 'assuranceLevel'];

export const IdentityPage: React.FC = () => {
  const { user } = useAuth(); // used for Keycloak subject fallback in UI
  const { pushToast } = useToast();
  const queryClient = useQueryClient();
  const [ekycSessionId, setEkycSessionId] = useState<string | null>(null);
  const [ekycBusy, setEkycBusy] = useState(false);
  const [ekycResult, setEkycResult] = useState<Record<string, unknown> | null>(null);

  const [selected, setSelected] = useState<Record<string, boolean>>({
    givenName: true,
    familyName: true,
    dateOfBirth: false,
    nationality: true,
    assuranceLevel: true,
  });

  const { data, isLoading, isError, refetch } = useQuery<IdentityMeResponse>({
    queryKey: ['identity-me'],
    enabled: !!user?.access_token,
    retry: 1,
    queryFn: async () => (await api.get('/api/identity/me')).data,
  });

  const payload = fields.reduce<Record<string, unknown>>((acc, key) => {
    if (selected[key] && data?.[key] != null) acc[key] = data[key];
    return acc;
  }, {});

  const startEkycSession = async () => {
    setEkycBusy(true);
    setEkycResult(null);
    try {
      const res = await api.post<{ sessionId: string }>('/api/identity/ekyc-session');
      setEkycSessionId(res.data.sessionId);
      pushToast('success', 'eKYC session ready — upload a document image.');
    } catch (e: unknown) {
      const msg = e && typeof e === 'object' && 'response' in e ? String((e as { response?: { data?: unknown } }).response?.data) : 'Failed to start session';
      pushToast('error', msg);
    } finally {
      setEkycBusy(false);
    }
  };

  const submitEkycDocument = async (file: File | null) => {
    if (!ekycSessionId || !file) {
      pushToast('error', 'Create a session and choose an image first.');
      return;
    }
    setEkycBusy(true);
    try {
      const form = new FormData();
      form.append('sessionId', ekycSessionId);
      form.append('documentImage', file);
      const res = await api.post<Record<string, unknown>>('/api/ekyc/verify', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setEkycResult(res.data);
      pushToast('success', String(res.data.status ?? 'Verification submitted'));
      await queryClient.invalidateQueries({ queryKey: ['identity-me'] });
    } catch (e: unknown) {
      const ax = e as { response?: { data?: { error?: string } } };
      pushToast('error', ax.response?.data?.error ?? 'eKYC upload failed');
    } finally {
      setEkycBusy(false);
    }
  };

  if (isLoading) return <div className="mx-auto w-full max-w-6xl px-6 py-10 text-sm text-slate-500">Loading identity claims...</div>;
  if (isError || !data) {
    return (
      <div className="mx-auto w-full max-w-6xl px-6 py-10">
        <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-red-700">
          Failed to load identity profile. <button className="underline" onClick={() => refetch()}>Retry</button>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-6xl px-6 py-10 grid grid-cols-1 lg:grid-cols-2 gap-6">
      <section className="rounded-2xl border bg-white p-6">
        <h1 className="text-2xl font-black">Identity Claims</h1>
        <p className="mt-1 text-sm text-slate-500">Manage your claim disclosure preferences.</p>

        <div className="mt-6 rounded-xl border border-indigo-100 bg-indigo-50/50 p-4 space-y-3">
          <h3 className="font-black text-sm text-indigo-900">Web eKYC (document upload)</h3>
          <p className="text-xs text-slate-600">
            Replaces the mobile QR flow: start a session, then upload an ID or sample image. The AI service scores risk when available.
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <button type="button" className="btn btn-primary text-sm py-2" disabled={ekycBusy} onClick={startEkycSession}>
              {ekycSessionId ? 'New session' : 'Start eKYC session'}
            </button>
            {ekycSessionId && (
              <span className="text-xs font-mono text-slate-600">
                session: {ekycSessionId.slice(0, 8)}…
              </span>
            )}
          </div>
          <label className="flex flex-col gap-2 text-sm">
            <span className="font-semibold text-slate-700">Document image</span>
            <input
              type="file"
              accept="image/*"
              disabled={!ekycSessionId || ekycBusy}
              onChange={(ev) => {
                const f = ev.target.files?.[0];
                if (f) void submitEkycDocument(f);
              }}
            />
          </label>
          {ekycResult && (
            <pre className="max-h-40 overflow-auto rounded-lg bg-white p-2 text-xs border">{JSON.stringify(ekycResult, null, 2)}</pre>
          )}
        </div>
        <div className="mt-4 space-y-3">
          {fields.map((field) => (
            <label key={field} className="flex items-center justify-between rounded-lg border p-3">
              <span className="font-semibold">{field}</span>
              <input
                type="checkbox"
                checked={!!selected[field]}
                onChange={(e) => setSelected((prev) => ({ ...prev, [field]: e.target.checked }))}
              />
            </label>
          ))}
        </div>
        <div className="mt-4 rounded-lg bg-slate-50 p-3 text-sm space-y-1">
          <div>Onboarding: {data.onboardingState || '-'}</div>
          <div>Assurance: {data.assuranceLevel || '-'}</div>
          <div>Risk score: {data.riskScore ?? '-'}</div>
          <div className="pt-2 border-t border-slate-200 text-xs">
            <span className="text-slate-500">Keycloak subject (for merchant “payer” field):</span>
            <code className="block mt-1 select-all break-all text-slate-800">{data.keycloakSub || user?.profile?.sub || '—'}</code>
          </div>
        </div>
      </section>

      <section className="rounded-2xl border bg-white p-6">
        <h2 className="text-xl font-black">Shareable Claim QR</h2>
        <p className="mt-1 text-sm text-slate-500">Show this QR to a merchant for consented verification.</p>
        <div className="mt-6 flex items-center justify-center">
          <div className="rounded-2xl border p-6">
            <QRCodeSVG value={JSON.stringify({ sub: data.keycloakSub || data.id, claims: payload })} size={220} />
          </div>
        </div>
        <pre className="mt-4 max-h-44 overflow-auto rounded-lg bg-slate-100 p-3 text-xs">{JSON.stringify(payload, null, 2)}</pre>
      </section>
    </div>
  );
};
