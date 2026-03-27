import React, { createContext, useContext, useEffect, useState } from 'react';
import axios from 'axios';

interface TenantBranding {
  name: string;
  slug: string;
  primaryColor: string;
  primaryForeground: string;
  logoUrl?: string;
}

interface TenantContextType {
  tenant: TenantBranding | null;
  loading: boolean;
}

const TenantContext = createContext<TenantContextType | undefined>(undefined);

export const TenantProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [tenant, setTenant] = useState<TenantBranding | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchTenant = async () => {
      const hostname = window.location.hostname; // e.g., banka.trustlayer.et (no port)
      const parts = hostname.split('.');
      // Only treat the first segment as a slug when there is a real subdomain (3+ parts).
      // For plain localhost or IP addresses, fall back to 'hub'.
      const slug = parts.length >= 3 ? parts[0] : 'hub';
      let active: TenantBranding = {
        name: 'Trust Layer Hub',
        slug: 'hub',
        primaryColor: '#6366f1',
        primaryForeground: '#ffffff',
      };

      try {
        const response = await axios.get<TenantBranding>(`/api/tenants/${slug}/branding`);
        active = {
          ...response.data,
          primaryForeground: response.data.primaryForeground || '#ffffff',
        };
        setTenant(active);
      } catch {
        setTenant(active);
      }
      
      // Update CSS Variables dynamically
      const root = document.documentElement;
      root.style.setProperty('--color-primary', active.primaryColor);
      root.style.setProperty('--color-primary-foreground', active.primaryForeground);
      
      setLoading(false);
    };

    fetchTenant();
  }, []);

  return (
    <TenantContext.Provider value={{ tenant, loading }}>
      {children}
    </TenantContext.Provider>
  );
};

export const useTenant = () => {
  const context = useContext(TenantContext);
  if (!context) throw new Error('useTenant must be used within TenantProvider');
  return context;
};
