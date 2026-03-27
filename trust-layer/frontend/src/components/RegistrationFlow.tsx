import React, { useState, useEffect } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { Client } from '@stomp/stompjs';
import { motion, AnimatePresence } from 'framer-motion';
import { Check, ArrowRight, Smartphone, ShieldCheck, Mail, IdCard, UserPlus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

type Step = 'INITIAL' | 'QR_HANDOFF' | 'VERIFYING' | 'COMPLETE';

export const RegistrationFlow: React.FC = () => {
    const { login } = useAuth();
    const [step, setStep] = useState<Step>('INITIAL');
    const [sessionId] = useState(() => crypto.randomUUID());

    const wsBaseUrl = import.meta.env.VITE_WS_BASE_URL || `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`;

    useEffect(() => {
        const client = new Client({
            brokerURL: `${wsBaseUrl}/ws`,
            onConnect: () => {
                client.subscribe(`/topic/session/${sessionId}`, (message) => {
                    const event = JSON.parse(message.body);
                    if (event.eventType === 'EKYC_COMPLETE') {
                        setStep('VERIFYING');
                    } else if (event.eventType === 'CREDENTIAL_BOUND') {
                        setStep('COMPLETE');
                    }
                });
            },
        });

        if (step === 'QR_HANDOFF') {
            client.activate();
        }

        return () => {
            client.deactivate();
        };
    }, [sessionId, step, wsBaseUrl]);

    const stepsUI = {
        INITIAL: (
            <motion.div 
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="max-w-md w-full glass-card p-10 rounded-xl space-y-6"
            >
                <div className="text-center space-y-2">
                    <UserPlus className="w-12 h-12 mx-auto text-primary" />
                    <h2 className="text-3xl font-heading font-black">Register Identity</h2>
                    <p className="text-slate-500">Secure. Federated. Federated Identity for Bank A.</p>
                </div>

                <div className="space-y-4">
                    <div className="group border-2 border-slate-100 hover:border-primary/30 p-4 rounded-xl transition-all cursor-pointer">
                        <div className="flex items-center gap-4">
                            <div className="bg-primary/10 p-3 rounded-lg text-primary">
                                <Mail />
                            </div>
                            <div className="flex-1">
                                <div className="font-bold">Email Registration</div>
                                <div className="text-xs text-slate-500">Standard registration through mail</div>
                            </div>
                        </div>
                    </div>
                </div>

                <button 
                   onClick={() => setStep('QR_HANDOFF')}
                   className="w-full btn btn-primary gap-2"
                >
                    Start Registration <ArrowRight className="w-4 h-4" />
                </button>

                <p className="text-center text-sm text-slate-600">
                  Already have an account?{' '}
                  <Link to="/signin" className="font-semibold text-primary hover:underline">
                    Sign in
                  </Link>
                </p>
            </motion.div>
        ),
        QR_HANDOFF: (
            <motion.div 
               initial={{ opacity: 0, y: 30 }}
               animate={{ opacity: 1, y: 0 }}
               className="max-w-4xl w-full grid grid-cols-1 md:grid-cols-2 gap-10 items-center px-6"
            >
                <div className="space-y-8">
                    <div className="space-y-4">
                        <div className="inline-flex bg-primary/10 text-primary px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider">
                           Step 2: Mobile Handoff
                        </div>
                        <h2 className="text-4xl lg:text-5xl font-heading font-black">
                           Continue on your <span className="text-primary italic underline decoration-wavy">Phone</span>
                        </h2>
                        <p className="text-lg text-slate-600 leading-relaxed">
                            Scan the secure QR code to begin the high-assurance eKYC process on your device.
                            Your identity will be verified using 3D liveness.
                        </p>
                    </div>

                    <div className="space-y-4">
                        <div className="flex items-center gap-3 text-slate-700">
                           <ShieldCheck className="w-5 h-5 text-emerald-500" />
                           <span className="font-medium">Hardware-backed Biometric Binding</span>
                        </div>
                        <div className="flex items-center gap-3 text-slate-700">
                           <Smartphone className="w-5 h-5 text-indigo-500" />
                           <span className="font-medium">Direct Push-Notification State Sync</span>
                        </div>
                    </div>
                </div>

                <div className="flex flex-col items-center gap-6">
                    <div className="p-8 bg-white rounded-3xl shadow-2xl shadow-primary/10 border-8 border-slate-50 rotate-1 transform-gpu hover:rotate-0 transition-transform duration-500">
                        <QRCodeSVG 
                            value={`trustlayer://register?sessionId=${sessionId}&tenant=hub`} 
                            size={280}
                            level="H"
                            includeMargin={false}
                        />
                    </div>
                    <div className="flex items-center gap-3 bg-white px-4 py-2 rounded-full border shadow-sm border-slate-100">
                        <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
                        <span className="text-xs font-bold text-slate-600 uppercase tracking-widest text-center">
                           Awaiting Mobile Handoff...
                        </span>
                    </div>
                </div>
            </motion.div>
        ),
        VERIFYING: (
            <motion.div 
               initial={{ opacity: 0 }}
               animate={{ opacity: 1 }}
               className="text-center space-y-6 max-w-md mx-auto"
            >
                <div className="relative w-32 h-32 mx-auto">
                    <div className="absolute inset-0 rounded-full border-4 border-slate-100" />
                    <div className="absolute inset-0 rounded-full border-t-4 border-primary animate-spin" />
                    <div className="absolute inset-0 flex items-center justify-center">
                        <IdCard className="w-12 h-12 text-primary" />
                    </div>
                </div>
                <div className="space-y-2">
                    <h2 className="text-2xl font-heading font-bold">Verifying eKYC...</h2>
                    <p className="text-slate-500">Wait a few moments while we process your liveness check.</p>
                </div>
            </motion.div>
        ),
        COMPLETE: (
            <motion.div 
               initial={{ opacity: 0, scale: 0.9 }}
               animate={{ opacity: 1, scale: 1 }}
               className="text-center space-y-6 max-w-md mx-auto bg-white p-12 rounded-3xl shadow-2xl border border-slate-100"
            >
                <div className="w-20 h-20 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto">
                    <Check className="w-10 h-10" />
                </div>
                <div className="space-y-2">
                    <h2 className="text-3xl font-heading font-bold">Success!</h2>
                    <p className="text-slate-600">Your federated identity is now active. You have been provisioned a new virtual card.</p>
                </div>
                <button onClick={login} className="btn btn-primary w-full">Go to Dashboard</button>
            </motion.div>
        )
    };

    return (
        <div className="flex-1 flex items-center justify-center px-6" aria-live="polite">
            <AnimatePresence mode="wait">
                {stepsUI[step]}
            </AnimatePresence>
        </div>
    );
};
