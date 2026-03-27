import React from 'react';
import axios from 'axios';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

type SessionView = {
  id: string;
  ipAddress?: string;
  start?: string;
  lastAccess?: string;
  clients?: string[];
};

export const SessionsPage: React.FC = () => {
  const { user } = useAuth();
  const { pushToast } = useToast();
  const headers = { Authorization: `Bearer ${user?.access_token}` };

  const { data = [], isLoading, isError, refetch } = useQuery<SessionView[]>({
    queryKey: ['active-sessions'],
    queryFn: async () => (await axios.get('/api/sessions/active', { headers })).data,
  });

  const revoke = async (id: string) => {
    if (!window.confirm('Revoke this session?')) return;
    await axios.delete(`/api/sessions/${id}`, { headers });
    pushToast('success', 'Session revoked');
    refetch();
  };

  return (
    <div className="mx-auto w-full max-w-6xl px-6 py-10">
      <h1 className="text-2xl font-black">Active Sessions</h1>
      <p className="mt-1 text-sm text-slate-500">Review and revoke your active sign-ins.</p>

      {isLoading && <div className="mt-4 text-sm text-slate-500">Loading sessions...</div>}
      {isError && (
        <div className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-red-700">
          Failed to load sessions. <button className="underline" onClick={() => refetch()}>Retry</button>
        </div>
      )}

      <div className="mt-6 space-y-3">
        {data.map((session) => (
          <div key={session.id} className="rounded-xl border bg-white p-4 flex items-center justify-between gap-4">
            <div className="text-sm">
              <div className="font-semibold">IP: {session.ipAddress || '-'}</div>
              <div className="text-slate-500">Start: {session.start ? new Date(session.start).toLocaleString() : '-'}</div>
              <div className="text-slate-500">Last access: {session.lastAccess ? new Date(session.lastAccess).toLocaleString() : '-'}</div>
              <div className="text-slate-500">Clients: {(session.clients || []).join(', ') || '-'}</div>
            </div>
            <button onClick={() => revoke(session.id)} className="rounded-lg bg-red-50 px-3 py-2 text-xs font-bold text-red-700">
              Revoke
            </button>
          </div>
        ))}
        {!isLoading && data.length === 0 && <div className="rounded-xl border bg-white p-4 text-sm text-slate-500">No active sessions found.</div>}
      </div>
    </div>
  );
};
