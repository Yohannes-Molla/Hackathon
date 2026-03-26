import React, { createContext, useContext, useEffect, useState } from 'react';

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

      // Mocked branding load, in production this is a GET /api/branding/slug
      const mockBranding: Record<string, TenantBranding> = {
        hub: {
          name: 'Trust Layer Hub',
          slug: 'hub',
          primaryColor: '#6366f1',
          primaryForeground: '#ffffff'
        },
        banka: {
          name: 'Bank A (NBE)',
          slug: 'banka',
          primaryColor: '#059669', // Emerald 600
          primaryForeground: '#ffffff'
        },
        bankb: {
          name: 'Bank B (CBE)',
          slug: 'bankb',
          primaryColor: '#7c3aed', // Violet 600
          primaryForeground: '#ffffff'
        }
      };

      const found = mockBranding[slug] || mockBranding.hub;
      setTenant(found);
      
      // Update CSS Variables dynamically
      const root = document.documentElement;
      root.style.setProperty('--color-primary', found.primaryColor);
      root.style.setProperty('--color-primary-foreground', found.primaryForeground);
      
      setLoading(false);
    };

    fetchTenant();
  }, []);

  return (
    <TenantContext.Provider value={{ tenant, loading }}>
      {!loading && children}
    </TenantContext.Provider>
  );
};

export const useTenant = () => {
  const context = useContext(TenantContext);
  if (!context) throw new Error('useTenant must be used within TenantProvider');
  return context;
};
