import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { hasRealmRole } from '../auth/keycloakRoles';
import { ProtectedRoute } from './ProtectedRoute';

export const RoleRoute: React.FC<{ role: string; children: React.ReactNode }> = ({ role, children }) => {
  const { user } = useAuth();

  return (
    <ProtectedRoute>
      {hasRealmRole(user, role) ? children : <Navigate to="/dashboard" replace />}
    </ProtectedRoute>
  );
};
