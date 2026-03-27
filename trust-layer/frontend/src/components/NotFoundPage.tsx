import React from 'react';
import { Link } from 'react-router-dom';

export const NotFoundPage: React.FC = () => {
  return (
    <div className="mx-auto w-full max-w-2xl px-6 py-16 text-center">
      <h1 className="text-3xl font-black">Page not found</h1>
      <p className="mt-2 text-slate-500">The page you requested does not exist.</p>
      <Link to="/" className="mt-6 inline-block rounded-lg bg-primary px-4 py-2 text-sm font-bold text-white">
        Go home
      </Link>
    </div>
  );
};
