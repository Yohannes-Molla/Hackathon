import React, { useState } from 'react';
import { 
    ArrowUpRight, 
    ArrowDownLeft, 
    Shield, 
    Eye, 
    EyeOff, 
    Settings, 
    LogOut,
    Plus,
    UserCheck,
    History,
    Check
} from 'lucide-react';
import { motion } from 'framer-motion';
import { useAuth } from '../context/AuthContext';
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';

interface Transaction {
    txId: string;
    type: 'DEBIT' | 'CREDIT';
    merchantId: string;
    amountMinor: number;
    status: string;
    timestamp: string;
}

interface VirtualCard {
    cardId: string;
    lastFour: string;
    cardNetwork: string;
    status: string;
    currency: string;
    dailyLimitMinor: number;
}

export const Dashboard: React.FC<{ onLogout: () => void }> = ({ onLogout }) => {
    const { user } = useAuth();
    const [showPan, setShowPan] = useState(false);

    // Dynamic fetching
    const userId = user?.profile?.sub || '00000000-0000-0000-0000-000000000000';

    const { data: cards } = useQuery<VirtualCard[]>({
        queryKey: ['cards', userId],
        queryFn: async () => {
            try {
                const res = await axios.get(`/api/vci/cards/${userId}`, {
                    headers: { Authorization: `Bearer ${user?.access_token}` }
                });
                return res.data;
            } catch (err) {
                console.warn('Backend unavailable, using fallback mock cards');
                return [{
                    cardId: 'mock-1',
                    lastFour: '1102',
                    cardNetwork: 'VISA',
                    status: 'ACTIVE',
                    currency: 'ETB',
                    dailyLimitMinor: 500000
                }];
            }
        }
    });

    const { data: transactions } = useQuery<Transaction[]>({
        queryKey: ['transactions', userId],
        queryFn: async () => {
            try {
                const res = await axios.get(`/api/tx/history/${userId}`, {
                    headers: { Authorization: `Bearer ${user?.access_token}` }
                });
                return res.data;
            } catch (err) {
                console.warn('Backend unavailable, using fallback mock tx history');
                return [
                    { txId: '1', type: 'DEBIT', merchantId: 'Addis Pharmacy', amountMinor: 120050, status: 'APPROVED', timestamp: '2026-03-26T10:30:00Z' },
                    { txId: '2', type: 'CREDIT', merchantId: 'Top-up Transfer', amountMinor: 500000, status: 'COMPLETED', timestamp: '2026-03-25T15:45:00Z' },
                    { txId: '3', type: 'DEBIT', merchantId: 'Netflix Subscription', amountMinor: 45025, status: 'APPROVED', timestamp: '2026-03-24T09:12:00Z' },
                ] as Transaction[];
            }
        }
    });

    const primaryCard = cards?.[0];

    return (
        <div className="max-w-6xl mx-auto w-full px-6 py-12 grid grid-cols-1 lg:grid-cols-12 gap-10">
            {/* Sidebar / Left Column */}
            <div className="lg:col-span-8 flex flex-col gap-10">
                <header className="space-y-4">
                    <h1 className="text-4xl font-heading font-black">Welcome back, <span className="text-primary italic">{user?.profile?.given_name || 'Yohannes'}!</span></h1>
                    <div className="flex items-center gap-3 bg-emerald-50 text-emerald-700 px-4 py-2 rounded-xl text-sm font-bold w-fit">
                        <UserCheck className="w-4 h-4" />
                        IAL2 Verified • 🛡️ Securely Federated
                    </div>
                </header>

                {/* Card Container */}
                <div className="relative group">
                    <motion.div 
                        initial={{ rotateY: -10, rotateX: 5 }}
                        whileHover={{ rotateY: 0, rotateX: 0 }}
                        className="bg-gradient-to-br from-slate-900 to-slate-800 p-10 rounded-[2.5rem] text-white shadow-2xl shadow-indigo-500/10 min-h-[340px] flex flex-col justify-between relative overflow-hidden border border-white/5"
                    >
                        {/* Shimmer effect */}
                        <div className="absolute inset-0 bg-gradient-to-tr from-white/10 via-transparent to-transparent pointer-events-none opacity-20" />
                        
                        <div className="flex justify-between items-start z-10">
                            <div className="space-y-1">
                                <div className="text-xs font-bold uppercase tracking-widest text-slate-400">Virtual Card Issuer</div>
                                <div className="text-2xl font-heading font-bold italic tracking-tighter">THE TRUST LAYER</div>
                            </div>
                            <div className="w-16 h-10 rounded-lg bg-white/20 backdrop-blur-md flex items-center justify-center border border-white/10">
                                <div className="w-10 h-8 bg-amber-400/80 rounded" />
                            </div>
                        </div>

                        <div className="z-10 space-y-4">
                            <div className="flex items-end gap-6">
                                <span className="text-4xl font-mono tracking-[0.4em] drop-shadow-lg">
                                    {showPan ? `4532 8821 0092 ${primaryCard?.lastFour || '1102'}` : `•••• •••• •••• ${primaryCard?.lastFour || '1102'}`}
                                </span>
                                <button 
                                   onClick={() => setShowPan(!showPan)}
                                   className="mb-1 p-2 bg-white/10 hover:bg-white/20 rounded-lg transition-colors"
                                >
                                    {showPan ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                                </button>
                            </div>
                            <div className="flex gap-10 text-sm opacity-80 uppercase tracking-widest font-bold">
                                <div>
                                   <div className="text-[10px] opacity-60">Expiry</div>
                                   <div>12/28</div>
                                </div>
                                <div>
                                   <div className="text-[10px] opacity-60">CVV</div>
                                   <div>{showPan ? '441' : '•••'}</div>
                                </div>
                                {primaryCard && (
                                   <div>
                                     <div className="text-[10px] opacity-60">Daily Limit</div>
                                     <div>{primaryCard.currency} {(primaryCard.dailyLimitMinor / 100).toLocaleString()}</div>
                                   </div>
                                )}
                            </div>
                        </div>

                        <div className="flex justify-between items-end z-10">
                            <div className="space-y-1">
                                <div className="text-[10px] opacity-60 uppercase font-bold tracking-widest">Cardholder Name</div>
                                <div className="text-lg font-heading font-medium tracking-wide translate-y-[-2px]">{user?.profile?.name || 'YOHANNES MOLLA'}</div>
                            </div>
                            <img src="https://upload.wikimedia.org/wikipedia/commons/5/5e/Visa_Inc._logo.svg" className="h-4 brightness-200" alt="Visa" />
                        </div>
                    </motion.div>
                </div>

                {/* History Section */}
                <section className="space-y-6">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <History className="w-6 h-6 text-primary" />
                            <h3 className="text-2xl font-heading font-black">Transaction Activity</h3>
                        </div>
                        <button className="text-xs font-bold text-primary px-3 py-1 bg-primary/5 rounded-full hover:bg-primary/10 transition-colors uppercase tracking-widest">View All</button>
                    </div>

                    <div className="bg-white rounded-3xl border border-slate-100 overflow-hidden shadow-sm">
                        <table className="w-full text-left">
                            <thead className="bg-slate-50/50 text-[10px] font-black uppercase tracking-widest text-slate-400 border-b">
                                <tr>
                                    <th className="px-8 py-4">Merchant</th>
                                    <th className="px-8 py-4">Status</th>
                                    <th className="px-8 py-4">Date</th>
                                    <th className="px-8 py-4 text-right">Amount</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-100">
                                {transactions?.map((tx) => {
                                    const amount = tx.amountMinor / 100;
                                    const type = tx.type || (tx.amountMinor > 0 ? 'DEBIT' : 'CREDIT');
                                    
                                    return (
                                    <tr key={tx.txId} className="hover:bg-slate-50 transition-colors group">
                                        <td className="px-8 py-5">
                                            <div className="flex items-center gap-4 text-sm font-bold text-slate-800">
                                                <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-slate-500 group-hover:bg-primary/10 group-hover:text-primary transition-colors">
                                                    {type === 'DEBIT' ? <ArrowUpRight className="w-4 h-4" /> : <ArrowDownLeft className="w-4 h-4" />}
                                                </div>
                                                {tx.merchantId}
                                            </div>
                                        </td>
                                        <td className="px-8 py-5">
                                            <span className="text-[10px] font-black text-emerald-600 bg-emerald-50 px-2 py-1 rounded-full">{tx.status}</span>
                                        </td>
                                        <td className="px-8 py-5 text-sm text-slate-400">{new Date(tx.timestamp).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</td>
                                        <td className="px-8 py-5 text-right font-mono font-bold text-slate-800">
                                            {type === 'DEBIT' ? '-' : '+'} ETB {amount.toLocaleString()}
                                        </td>
                                    </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </section>
            </div>

            {/* Right Column / Quick Actions */}
            <div className="lg:col-span-4 flex flex-col gap-10">
                <section className="glass-card p-8 rounded-3xl space-y-6 shadow-2xl shadow-slate-200/50">
                    <h4 className="text-xl font-heading font-black">Identity Shield</h4>
                    <div className="flex flex-col gap-4">
                        <div className="flex justify-between items-center text-sm">
                            <span className="text-slate-500">Selective Disclosure</span>
                            <div className="w-10 h-5 bg-primary/20 rounded-full relative">
                                <div className="absolute right-1 top-1 bottom-1 w-3 bg-primary rounded-full" />
                            </div>
                        </div>
                        <div className="flex justify-between items-center text-sm">
                            <span className="text-slate-500">Biometric Auth</span>
                            <Check className="w-4 h-4 text-emerald-500" />
                        </div>
                        <p className="text-xs text-slate-400 leading-relaxed pt-2 border-t mt-2">
                           Your hardware-backed identity ensures maximum compliance with NBE regulations. Every transaction is biometric-gated.
                        </p>
                    </div>
                </section>

                <div className="flex flex-col gap-3">
                   <button className="btn bg-white border border-slate-200 text-slate-900 w-full gap-3 shadow-sm hover:border-slate-300 transition-colors">
                       <Shield className="w-5 h-5 text-primary" />
                       Verify ID at Merchant
                   </button>
                   <button className="btn bg-white border border-slate-200 text-slate-900 w-full gap-3 shadow-sm hover:border-slate-300 transition-colors">
                       <Plus className="w-5 h-5 text-primary" />
                       Request Limit Increase
                   </button>
                   <button className="btn bg-slate-50 text-slate-600 border border-transparent w-full gap-3 mt-6 hover:bg-slate-100 transition-colors">
                       <Settings className="w-5 h-5" />
                       Settings
                   </button>
                   <button 
                      onClick={onLogout}
                      className="btn text-red-600 bg-red-50 hover:bg-red-100 transition-colors w-full gap-3"
                   >
                       <LogOut className="w-5 h-5" />
                       Logout Session
                   </button>
                </div>
            </div>
        </div>
    );
};
