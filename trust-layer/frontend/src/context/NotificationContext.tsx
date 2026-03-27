import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { useAuth } from './AuthContext';
import { useToast } from './ToastContext';

type NotificationItem = {
  id: string;
  title: string;
  createdAt: string;
};

type NotificationContextType = {
  notifications: NotificationItem[];
  unreadCount: number;
  markAllRead: () => void;
};

const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

export const NotificationProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, isAuthenticated } = useAuth();
  const { pushToast } = useToast();
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);

  useEffect(() => {
    if (!isAuthenticated || !user?.profile?.sub) return;
    const wsBaseUrl =
      import.meta.env.VITE_WS_BASE_URL || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`;

    const client = new Client({
      brokerURL: `${wsBaseUrl}/ws`,
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/session/${user.profile.sub}`, (message) => {
          try {
            const payload = JSON.parse(message.body) as { eventType?: string };
            const title = payload.eventType ? `Event: ${payload.eventType}` : 'New update received';
            const item: NotificationItem = {
              id: crypto.randomUUID(),
              title,
              createdAt: new Date().toISOString(),
            };
            setNotifications((prev) => [item, ...prev].slice(0, 30));
            setUnreadCount((prev) => prev + 1);
            pushToast('info', title);
          } catch {
            pushToast('warning', 'Received a malformed realtime event');
          }
        });
      },
    });

    client.activate();
    return () => {
      client.deactivate();
    };
  }, [isAuthenticated, pushToast, user?.profile?.sub]);

  const value = useMemo(
    () => ({
      notifications,
      unreadCount,
      markAllRead: () => setUnreadCount(0),
    }),
    [notifications, unreadCount],
  );

  return <NotificationContext.Provider value={value}>{children}</NotificationContext.Provider>;
};

export const useNotifications = () => {
  const context = useContext(NotificationContext);
  if (!context) throw new Error('useNotifications must be used within NotificationProvider');
  return context;
};
