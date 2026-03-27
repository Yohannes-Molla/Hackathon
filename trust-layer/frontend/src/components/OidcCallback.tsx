import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { userManager } from '../context/AuthContext';
import { POST_LOGIN_REDIRECT_KEY } from '../auth/postLoginRedirect';

function safePostLoginPath(raw: string | null): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//')) return '/dashboard';
  return raw;
}

export const OidcCallback: React.FC = () => {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const handled = useRef(false);

  useEffect(() => {
    // StrictMode double-mounts effects; the auth code can only be exchanged once.
    if (handled.current) return;
    handled.current = true;

    userManager
      .signinRedirectCallback()
      .then(() => {
        const stored = sessionStorage.getItem(POST_LOGIN_REDIRECT_KEY);
        sessionStorage.removeItem(POST_LOGIN_REDIRECT_KEY);
        navigate(safePostLoginPath(stored), { replace: true });
      })
      .catch((e: unknown) => {
        const message = e instanceof Error ? e.message : 'Sign-in failed';
        setError(message);
      });
  }, [navigate]);

  if (error) {
    return (
      <div className="flex-1 flex items-center justify-center px-6">
        <div className="max-w-md text-center space-y-4 glass-card p-10 rounded-xl">
          <h1 className="text-xl font-heading font-bold text-red-600">Sign-in failed</h1>
          <p className="text-slate-600 text-sm">{error}</p>
          <button type="button" className="btn btn-primary" onClick={() => navigate('/', { replace: true })}>
            Back to home
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 flex items-center justify-center px-6">
      <div className="text-center space-y-3">
        <div className="w-12 h-12 border-4 border-slate-200 border-t-primary rounded-full animate-spin mx-auto" />
        <p className="text-sm font-medium text-slate-600">Completing sign-in…</p>
      </div>
    </div>
  );
};
