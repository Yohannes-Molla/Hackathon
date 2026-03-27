import React, { useState } from 'react';
import { ArrowDownLeft, ArrowUpRight, Check, Eye, EyeOff, History, LogOut, Plus, Settings, Shield, UserCheck } from 'lucide-react';
import { motion } from 'framer-motion';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useQuery } from '@tanstack/react-query';
import api from '../api/client';
import { QRCodeSVG } from 'qrcode.react';
import { useToast } from '../context/ToastContext';

interface Transaction {
    txId: string;
    merchantId: string;
    amountMinor: number;
    status: string;
    currency: string;
    timestamp: string;
    riskScore?: number;
}

interface VirtualCard {
    cardId: string;
    lastFour: string;
    cardNetwork: string;
    status: string;
    currency: string;
    dailyLimitMinor: number;
}

interface DynamicCvvResponse {
    cardId: string;
    cvv: string;
}

export const Dashboard: React.FC = () => {
    const navigate = useNavigate();
    const { user, logout } = useAuth();
    const { pushToast } = useToast();
    const [showPan, setShowPan] = useState(false);
    const [showVerifyModal, setShowVerifyModal] = useState(false);
    const [selectedClaims, setSelectedClaims] = useState<Record<string, boolean>>({
        givenName: true,
        familyName: true,
        nationality: true,
        assuranceLevel: true,
    });

    const userId = user?.profile?.sub || '';

    const { data: cards, isLoading: cardsLoading, isError: cardsError, refetch: refetchCards } = useQuery<VirtualCard[]>({
        queryKey: ['cards', userId],
        enabled: !!userId,
        retry: 1,
        queryFn: async () => (await api.get(`/api/vci/cards/${userId}`)).data,
    });

    const { data: transactions, isLoading: txLoading, isError: txError, refetch: refetchTx } = useQuery<Transaction[]>({
        queryKey: ['transactions', userId],
        enabled: !!userId,
        retry: 1,
        queryFn: async () => (await api.get(`/api/tx/history/${userId}`)).data,
    });

    const primaryCard = cards?.[0];
    const { data: cvv } = useQuery<DynamicCvvResponse>({
        queryKey: ['cvv', primaryCard?.cardId],
        enabled: !!primaryCard?.cardId,
        refetchInterval: 60_000,
        retry: 1,
        queryFn: async () => (await api.get(`/api/vci/cards/${primaryCard?.cardId}/cvv`)).data,
    });

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
                    {cardsLoading && <div className="rounded-3xl border bg-white p-6 text-sm text-slate-500">Loading card...</div>}
                    {cardsError && (
                        <div className="rounded-3xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                            Failed to load card. <button className="underline" onClick={() => refetchCards()}>Retry</button>
                        </div>
                    )}
                    {!cardsLoading && !cardsError && !primaryCard && (
                        <div className="rounded-3xl border bg-white p-6 text-sm text-slate-500">No card provisioned yet.</div>
                    )}
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
                                    {showPan ? `**** **** **** ${primaryCard?.lastFour || '----'}` : `•••• •••• •••• ${primaryCard?.lastFour || '----'}`}
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
                                   <div>--/--</div>
                                </div>
                                <div>
                                   <div className="text-[10px] opacity-60">CVV</div>
                                   <div>{showPan ? (cvv?.cvv || '•••') : '•••'}</div>
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
                        <Link to="/transactions" className="text-xs font-bold text-primary px-3 py-1 bg-primary/5 rounded-full hover:bg-primary/10 transition-colors uppercase tracking-widest">View All</Link>
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
                                {txLoading && (
                                    <tr><td className="px-8 py-5 text-sm text-slate-500" colSpan={4}>Loading transactions...</td></tr>
                                )}
                                {txError && (
                                    <tr>
                                        <td className="px-8 py-5 text-sm text-red-700" colSpan={4}>
                                            Failed to load transactions. <button className="underline" onClick={() => refetchTx()}>Retry</button>
                                        </td>
                                    </tr>
                                )}
                                {transactions?.map((tx) => {
                                    const amount = tx.amountMinor / 100;
                                    const type = tx.amountMinor > 0 ? 'DEBIT' : 'CREDIT';
                                    
                                    return (
                                    <tr key={tx.txId} className="hover:bg-slate-50 transition-colors group cursor-pointer" onClick={() => navigate(`/transactions/${tx.txId}`, { state: { tx } })}>
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
                                            {type === 'DEBIT' ? '-' : '+'} {tx.currency || 'ETB'} {amount.toLocaleString()}
                                        </td>
                                    </tr>
                                    );
                                })}
                                {!txLoading && !txError && (!transactions || transactions.length === 0) && (
                                    <tr><td className="px-8 py-5 text-sm text-slate-500" colSpan={4}>No transactions yet.</td></tr>
                                )}
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
                   <button className="btn bg-white border border-slate-200 text-slate-900 w-full gap-3 shadow-sm hover:border-slate-300 transition-colors" onClick={() => setShowVerifyModal(true)}>
                       <Shield className="w-5 h-5 text-primary" />
                       Verify ID at Merchant
                   </button>
                   <Link to="/cards" className="btn bg-white border border-slate-200 text-slate-900 w-full gap-3 shadow-sm hover:border-slate-300 transition-colors">
                       <Plus className="w-5 h-5 text-primary" />
                       Request Limit Increase
                   </Link>
                   <Link to="/settings" className="btn bg-slate-50 text-slate-600 border border-transparent w-full gap-3 mt-6 hover:bg-slate-100 transition-colors">
                       <Settings className="w-5 h-5" />
                       Settings
                   </Link>
                   <button 
                      onClick={() => logout()}
                      className="btn text-red-600 bg-red-50 hover:bg-red-100 transition-colors w-full gap-3"
                   >
                       <LogOut className="w-5 h-5" />
                       Logout Session
                   </button>
                </div>
            </div>
            {showVerifyModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
                    <div className="w-full max-w-md rounded-2xl bg-white p-6">
                        <h4 className="text-lg font-black">Share Claims QR</h4>
                        <p className="mt-1 text-sm text-slate-500">Select claims and generate a merchant verification QR.</p>
                        <div className="mt-3 space-y-2 text-sm">
                            {Object.keys(selectedClaims).map((claim) => (
                                <label key={claim} className="flex items-center justify-between rounded border p-2">
                                    <span>{claim}</span>
                                    <input
                                        type="checkbox"
                                        checked={selectedClaims[claim]}
                                        onChange={(e) => setSelectedClaims((prev) => ({ ...prev, [claim]: e.target.checked }))}
                                    />
                                </label>
                            ))}
                        </div>
                        <div className="mt-4 flex justify-center rounded-xl border p-4">
                            <QRCodeSVG
                                value={JSON.stringify({
                                    sub: userId,
                                    claims: Object.fromEntries(Object.entries(selectedClaims).filter(([, enabled]) => enabled)),
                                })}
                                size={180}
                            />
                        </div>
                        <div className="mt-4 flex justify-end gap-2">
                            <button className="rounded-lg border px-3 py-2 text-sm" onClick={() => setShowVerifyModal(false)}>Close</button>
                            <button
                                className="rounded-lg bg-primary px-3 py-2 text-sm font-bold text-white"
                                onClick={() => {
                                    pushToast('success', 'Claim QR generated');
                                    setShowVerifyModal(false);
                                }}
                            >
                                Done
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};
