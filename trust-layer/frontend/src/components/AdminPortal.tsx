import React, { useState } from 'react';
import { Building, Users, Activity, ChevronRight, User, Hash, Globe } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

// Mock Data
const MOCK_TENANTS = [
    { id: 't1', name: 'Bank of Abyssinia', slug: 'BOA', domain: 'boa.trustlayer.et', usersCount: 1250, status: 'ACTIVE' },
    { id: 't2', name: 'Commercial Bank of Ethiopia', slug: 'CBE', domain: 'cbe.trustlayer.et', usersCount: 8430, status: 'ACTIVE' },
    { id: 't3', name: 'Awash Bank', slug: 'AWASH', domain: 'awash.trustlayer.et', usersCount: 412, status: 'PENDING' },
];

const MOCK_USERS = {
    't1': [
        { id: 'u1', name: 'Yohannes Molla', email: 'yohannes@example.com', onboardState: 'LIVE', level: 'IAL2', created: '2026-01-15' },
        { id: 'u2', name: 'Abebe Bikila', email: 'abebe@example.com', onboardState: 'EKYC_COMPLETE', level: 'IAL2', created: '2026-02-12' },
        { id: 'u3', name: 'Tarikua Alemu', email: 'tarikua@example.com', onboardState: 'PENDING', level: 'IAL1', created: '2026-03-20' },
    ],
    't2': [
        { id: 'u4', name: 'Sara Yilma', email: 'sara@example.com', onboardState: 'LIVE', level: 'IAL3', created: '2025-11-05' },
        { id: 'u5', name: 'Dawit Getachew', email: 'dawit@example.com', onboardState: 'LIVE', level: 'IAL2', created: '2026-01-22' },
    ],
    't3': []
};

const MOCK_TXS = {
    'u1': [
        { id: 'tx1', merchant: 'Addis Supermarket', amount: 1250.00, status: 'APPROVED', date: '2026-03-25T14:30:00Z' },
        { id: 'tx2', merchant: 'Ethio Telecom', amount: 250.00, status: 'APPROVED', date: '2026-03-24T09:15:00Z' },
    ],
    'u4': [
        { id: 'tx3', merchant: 'Ethiopian Airlines', amount: 15400.00, status: 'APPROVED', date: '2026-03-26T08:10:00Z' },
        { id: 'tx4', merchant: 'Zemen Cafe', amount: 450.00, status: 'REJECTED', date: '2026-03-26T12:00:00Z' },
    ]
};

export const AdminPortal: React.FC = () => {
    const [selectedTenant, setSelectedTenant] = useState<string | null>(null);
    const [selectedUser, setSelectedUser] = useState<string | null>(null);

    const activeUsers = selectedTenant ? MOCK_USERS[selectedTenant as keyof typeof MOCK_USERS] : [];
    const activeTxs = selectedUser ? MOCK_TXS[selectedUser as keyof typeof MOCK_TXS] || [] : [];

    return (
        <div className="max-w-7xl mx-auto w-full px-6 py-8">
            <header className="mb-10 flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-heading font-black flex items-center gap-3">
                        <Globe className="text-primary w-8 h-8" />
                        Admin Portal
                    </h1>
                    <p className="text-slate-500 mt-1">Manage federated tenants, users, and transaction limits.</p>
                </div>
                <div className="flex gap-4">
                    <div className="bg-white border rounded-lg px-4 py-2 text-sm text-center shadow-sm">
                        <div className="text-xs font-bold text-slate-400 uppercase tracking-widest">Total Tenants</div>
                        <div className="font-mono font-black text-xl text-slate-800">{MOCK_TENANTS.length}</div>
                    </div>
                </div>
            </header>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 h-[70vh]">
                {/* Panel 1: Tenants */}
                <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden flex flex-col shadow-sm hover:shadow-md transition-shadow">
                    <div className="bg-slate-50 px-6 py-4 border-b border-slate-200 flex items-center justify-between z-10">
                        <h2 className="font-heading font-black text-slate-800 flex items-center gap-2">
                            <Building className="w-4 h-4 text-primary" />
                            Tenants
                        </h2>
                    </div>
                    <div className="flex-1 overflow-y-auto p-4 space-y-3">
                        {MOCK_TENANTS.map(tenant => (
                            <div 
                                key={tenant.id} 
                                onClick={() => { setSelectedTenant(tenant.id); setSelectedUser(null); }}
                                className={`p-4 rounded-xl cursor-pointer border transition-colors ${selectedTenant === tenant.id ? 'bg-primary/5 border-primary shadow-sm' : 'border-slate-100 hover:border-slate-300 bg-white'}`}
                            >
                                <div className="flex justify-between items-start">
                                    <div className="font-bold text-sm text-slate-800">{tenant.name}</div>
                                    <span className={`text-[10px] font-black uppercase px-2 py-0.5 rounded-full ${tenant.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>
                                        {tenant.status}
                                    </span>
                                </div>
                                <div className="text-xs text-slate-500 mt-2 flex items-center gap-4">
                                    <span>{tenant.slug}</span>
                                    <span className="flex items-center gap-1"><Users className="w-3 h-3" /> {tenant.usersCount}</span>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Panel 2: Users */}
                <div className={`bg-white rounded-2xl border overflow-hidden flex flex-col shadow-sm transition-opacity duration-300 ${!selectedTenant ? 'opacity-50 pointer-events-none border-slate-200' : 'border-primary/20 hover:shadow-md'}`}>
                    <div className="bg-slate-50 px-6 py-4 border-b border-slate-200 flex items-center justify-between">
                        <h2 className="font-heading font-black text-slate-800 flex items-center gap-2">
                            <Users className="w-4 h-4 text-indigo-500" />
                            Tenant Users
                        </h2>
                        {selectedTenant && <span className="text-xs bg-slate-200 px-2 py-1 rounded font-bold">{activeUsers.length} Found</span>}
                    </div>
                    <div className="flex-1 overflow-y-auto p-4 space-y-3">
                        {!selectedTenant ? (
                            <div className="h-full flex items-center justify-center text-slate-400 text-sm font-medium">Select a tenant to view users</div>
                        ) : activeUsers.length === 0 ? (
                            <div className="text-center text-slate-400 text-sm mt-10">No users found for this tenant.</div>
                        ) : (
                            activeUsers.map(user => (
                                <div 
                                    key={user.id} 
                                    onClick={() => setSelectedUser(user.id)}
                                    className={`p-4 rounded-xl cursor-pointer border transition-colors ${selectedUser === user.id ? 'bg-indigo-50 border-indigo-400 shadow-sm' : 'border-slate-100 hover:border-slate-300 bg-white'}`}
                                >
                                    <div className="flex justify-between items-start mb-1">
                                        <div className="font-bold text-sm text-slate-800 flex items-center gap-2">
                                            <User className="w-3 h-3 text-slate-400" />
                                            {user.name}
                                        </div>
                                        <ChevronRight className={`w-4 h-4 text-slate-300 transition-transform ${selectedUser === user.id ? 'translate-x-1 text-indigo-500' : ''}`} />
                                    </div>
                                    <div className="text-xs text-slate-500 truncate mb-3">{user.email}</div>
                                    <div className="flex items-center gap-2">
                                        <span className={`text-[9px] font-black uppercase px-2 py-0.5 rounded ${user.onboardState === 'LIVE' ? 'bg-emerald-100 text-emerald-700' : user.onboardState === 'EKYC_COMPLETE' ? 'bg-blue-100 text-blue-700' : 'bg-slate-100 text-slate-600'}`}>
                                            {user.onboardState}
                                        </span>
                                        <span className="text-[9px] font-black uppercase px-2 py-0.5 rounded bg-amber-100 text-amber-700 border border-amber-200">
                                            {user.level}
                                        </span>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </div>

                {/* Panel 3: Transactions */}
                <div className={`bg-white rounded-2xl border overflow-hidden flex flex-col shadow-sm transition-opacity duration-300 ${!selectedUser ? 'opacity-50 pointer-events-none border-slate-200' : 'border-emerald-500/30 hover:shadow-md'}`}>
                    <div className="bg-slate-50 px-6 py-4 border-b border-slate-200 flex items-center justify-between">
                        <h2 className="font-heading font-black text-slate-800 flex items-center gap-2">
                            <Activity className="w-4 h-4 text-emerald-500" />
                            User Transactions
                        </h2>
                    </div>
                    <div className="flex-1 overflow-y-auto p-4 space-y-3 bg-slate-50/50">
                        {!selectedUser ? (
                            <div className="h-full flex items-center justify-center text-slate-400 text-sm font-medium">Select a user to view transactions</div>
                        ) : activeTxs.length === 0 ? (
                            <div className="text-center text-slate-400 text-sm mt-10 p-6 bg-white rounded-xl border border-dashed border-slate-300">
                                No transactions found.<br/>
                                <span className="text-xs mt-2 block">Ensure the user has a provisioned virtual card.</span>
                            </div>
                        ) : (
                            <AnimatePresence>
                                {activeTxs.map(tx => (
                                    <motion.div 
                                        initial={{ opacity: 0, y: 10 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        key={tx.id} 
                                        className="bg-white p-4 rounded-xl border border-slate-100 shadow-sm"
                                    >
                                        <div className="flex justify-between items-start mb-2">
                                            <div className="font-bold text-sm text-slate-800">{tx.merchant}</div>
                                            <span className={`text-[10px] font-black uppercase px-2 py-0.5 rounded-full ${tx.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-600' : 'bg-red-50 text-red-600'}`}>
                                                {tx.status}
                                            </span>
                                        </div>
                                        <div className="flex justify-between items-end mt-4">
                                            <div className="text-[10px] text-slate-400 flex items-center gap-1">
                                                <Hash className="w-3 h-3" />
                                                {tx.id.toUpperCase()}
                                            </div>
                                            <div className="font-mono font-bold text-slate-800 text-sm">
                                                {tx.amount.toLocaleString()} ETB
                                            </div>
                                        </div>
                                    </motion.div>
                                ))}
                            </AnimatePresence>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};
