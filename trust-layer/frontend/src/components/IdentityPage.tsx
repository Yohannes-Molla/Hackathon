import React, { useState } from 'react';
import axios from 'axios';
import { useQuery } from '@tanstack/react-query';
import { QRCodeSVG } from 'qrcode.react';
import { useAuth } from '../context/AuthContext';

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
  const { user } = useAuth();
  const [selected, setSelected] = useState<Record<string, boolean>>({
    givenName: true,
    familyName: true,
    dateOfBirth: false,
    nationality: true,
    assuranceLevel: true,
  });

  const { data, isLoading, isError, refetch } = useQuery<IdentityMeResponse>({
    queryKey: ['identity-me'],
    queryFn: async () => (await axios.get('/api/identity/me', { headers: { Authorization: `Bearer ${user?.access_token}` } })).data,
  });

  const payload = fields.reduce<Record<string, unknown>>((acc, key) => {
    if (selected[key] && data?.[key] != null) acc[key] = data[key];
    return acc;
  }, {});

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
        <div className="mt-4 rounded-lg bg-slate-50 p-3 text-sm">
          <div>Onboarding: {data.onboardingState || '-'}</div>
          <div>Assurance: {data.assuranceLevel || '-'}</div>
          <div>Risk score: {data.riskScore ?? '-'}</div>
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
