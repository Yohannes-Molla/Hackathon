import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';

export const SettingsPage: React.FC = () => {
  const { user } = useAuth();
  const { theme, toggleTheme } = useTheme();

  return (
    <div className="mx-auto w-full max-w-4xl px-6 py-10">
      <h1 className="text-2xl font-black">Settings</h1>
      <div className="mt-4 rounded-2xl border bg-white p-6 space-y-3">
        <div className="text-sm text-slate-500">Signed in as</div>
        <div className="font-semibold">{user?.profile?.name || user?.profile?.sub || '-'}</div>
        <button onClick={toggleTheme} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-bold text-white">
          Switch to {theme === 'light' ? 'dark' : 'light'} mode
        </button>
        <div className="pt-2 text-sm">
          <Link className="text-primary underline" to="/sessions">Manage sessions</Link>
          <span className="mx-2">•</span>
          <Link className="text-primary underline" to="/identity">Manage identity claims</Link>
        </div>
      </div>
    </div>
  );
};
