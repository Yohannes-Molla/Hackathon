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
      // Determine tenant from subdomain or X-Tenant-ID
      const host = window.location.host; // e.g., banka.trustlayer.et:3000
      const slug = host.split('.')[0] || 'hub';
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
