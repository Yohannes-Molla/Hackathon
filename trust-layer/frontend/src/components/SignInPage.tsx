import React from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { LogIn, Shield, ArrowLeft } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { POST_LOGIN_REDIRECT_KEY } from '../auth/postLoginRedirect';

export const SignInPage: React.FC = () => {
  const { isAuthenticated, isLoading, login } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname;

  const handleSignIn = () => {
    const target = from && from !== '/signin' ? from : '/dashboard';
    sessionStorage.setItem(POST_LOGIN_REDIRECT_KEY, target);
    login();
  };

  if (isLoading) {
    return (
      <div className="flex flex-1 min-h-[60vh] items-center justify-center px-6">
        <div className="h-12 w-12 animate-spin rounded-full border-4 border-slate-200 border-t-primary" />
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="flex flex-1 flex-col items-center justify-center px-4 py-12 sm:py-16">
      <div className="w-full max-w-md">
        <div className="glass-card overflow-hidden rounded-2xl border border-slate-200/80 bg-white/90 shadow-xl dark:border-slate-700 dark:bg-slate-900/80">
          <div className="bg-gradient-to-br from-primary/15 via-white to-slate-50 px-8 pb-6 pt-10 dark:from-primary/20 dark:via-slate-900 dark:to-slate-900">
            <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-primary text-2xl text-white shadow-lg shadow-primary/25">
              <Shield className="h-8 w-8" aria-hidden />
            </div>
            <h1 className="mt-6 text-center font-heading text-3xl font-black tracking-tight text-slate-900 dark:text-slate-50">
              Sign in
            </h1>
            <p className="mt-2 text-center text-sm text-slate-600 dark:text-slate-400">
              Use your organization account to access the Trust Layer dashboard, identity, and cards.
            </p>
          </div>

          <div className="space-y-6 px-8 pb-8 pt-2">
            <button
              type="button"
              onClick={handleSignIn}
              className="btn btn-primary flex w-full gap-2 py-3.5 text-base font-semibold shadow-md"
            >
              <LogIn className="h-5 w-5" aria-hidden />
              Continue with SSO
            </button>

            <p className="text-center text-xs text-slate-500 dark:text-slate-500">
              You will be redirected to the secure identity provider to sign in. No password is collected on this page.
            </p>

            <div className="relative">
              <div className="absolute inset-0 flex items-center" aria-hidden>
                <span className="w-full border-t border-slate-200 dark:border-slate-700" />
              </div>
              <div className="relative flex justify-center text-[10px] font-black uppercase tracking-widest text-slate-400">
                <span className="bg-white px-3 dark:bg-slate-900">New here?</span>
              </div>
            </div>

            <Link
              to="/"
              className="flex items-center justify-center gap-2 text-sm font-semibold text-primary hover:text-primary/80"
            >
              <ArrowLeft className="h-4 w-4" aria-hidden />
              Start identity registration instead
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
};
