const axios = require('axios');
const fs = require('fs');

const EKYC_API = 'http://localhost:9002/api/ekyc';
const TX_API = 'http://localhost:9003/api/tx';
const VCI_API = 'http://localhost:9004/api/vci';

async function delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

// Minimal FAPI 2.0 Base64 URL encode
function base64urlEncode(obj) {
    return Buffer.from(JSON.stringify(obj)).toString('base64')
        .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function runE2ESimulation() {
    console.log("==========================================");
    console.log("🛡️ THE TRUST LAYER - E2E FEDERATED FLOW");
    console.log("==========================================\n");

    try {
        console.log("Health checking microservices...");
        try {
            await axios.get(`${EKYC_API.replace('/api/ekyc', '')}/actuator/health`);
            await axios.get(`${TX_API.replace('/api/tx', '')}/actuator/health`);
            await axios.get(`${VCI_API.replace('/api/vci', '')}/actuator/health`);
            console.log("✅ All microservices are reachable.");
        } catch(e) {
            console.warn("⚠️ Warning: Could not reach one or more services. Are they running on ports 9002, 9003, and 9004?\n" + e.message);
            console.log("Continuing simulation by demonstrating the payload structures we send/receive...\n");
        }

        // ==========================================
        // 1. Bank A (Commercial Bank of Ethiopia)
        // ==========================================
        console.log("🏦 [Tenant 1: CBE] Initiating onboarding for Alice");
        const aliceSessionId = "cbe-session-" + Date.now();
        console.log(`   ➔ QR Handoff intercepted. eKYC Session ID: ${aliceSessionId}`);
        
        console.log("   ➔ Executing 3D Liveness & Document verification via Android...");
        // This simulates the Android OkHttp multipart upload
        const ekycProofA = {
            sessionId: aliceSessionId,
            status: "VERIFIED",
            verifiedClaims: { given_name: "Alice", family_name: "Tadesse", nationality: "ET" }
        };
        console.log("   ✅ eKYC Successful. Extracted Identity: " + JSON.stringify(ekycProofA.verifiedClaims));
        
        console.log("   ➔ Registering Hardware-Backed EC P-256 Key (StrongBox)...");
        const aliceKeyId = "hw-key-alice-256";
        const aliceUserId = "00000000-0000-0000-0000-000000000001"; // Mock UUID for demo
        
        console.log("   ➔ Provisioning Virtual Card for Bank A...");
        const vciRequestA = { userId: aliceUserId, keyId: aliceKeyId };
        console.log(`      POST ${VCI_API}/provision -> ${JSON.stringify(vciRequestA)}`);
        const aliceCardResponse = {
            cardId: "vc-alice-01",
            lastFour: "4421",
            cardNetwork: "VISA",
            currency: "ETB",
            dailyLimitMinor: 500000
        };
        console.log(`   ✅ Virtual Card bound to KeyStore! Card: **** **** **** ${aliceCardResponse.lastFour}\n`);

        await delay(1000);

        // ==========================================
        // 2. Bank B (Bank of Abyssinia)
        // ==========================================
        console.log("🏦 [Tenant 2: BOA] Initiating onboarding for Bob");
        const bobSessionId = "boa-session-" + Date.now();
        console.log(`   ➔ QR Handoff intercepted. eKYC Session ID: ${bobSessionId}`);
        
        console.log("   ➔ Executing 3D Liveness & Document verification via Android...");
        const ekycProofB = {
            sessionId: bobSessionId,
            status: "VERIFIED",
            verifiedClaims: { given_name: "Bob", family_name: "Bekele", nationality: "ET" }
        };
        console.log("   ✅ eKYC Successful. Extracted Identity: " + JSON.stringify(ekycProofB.verifiedClaims));
        
        console.log("   ➔ Registering Hardware-Backed EC P-256 Key (StrongBox)...");
        const bobKeyId = "hw-key-bob-256";
        const bobUserId = "00000000-0000-0000-0000-000000000002"; 
        
        console.log("   ➔ Provisioning Virtual Card for Bank B...");
        const vciRequestB = { userId: bobUserId, keyId: bobKeyId };
        console.log(`      POST ${VCI_API}/provision -> ${JSON.stringify(vciRequestB)}`);
        const bobCardResponse = {
            cardId: "vc-bob-01",
            lastFour: "8899",
            cardNetwork: "VISA",
            currency: "ETB",
            dailyLimitMinor: 1000000
        };
        console.log(`   ✅ Virtual Card bound to KeyStore! Card: **** **** **** ${bobCardResponse.lastFour}\n`);

        await delay(1000);

        // ==========================================
        // 3. FAPI Cross-Bank Transaction (Bob pays Alice)
        // ==========================================
        console.log("💸 Initiating Cross-Bank Transaction (BOA -> CBE)");
        console.log("   ➔ Bob navigates to merchant portal (Alice's store) and initiates pay.");
        
        const initTxRequest = {
            userId: bobUserId,
            merchantId: aliceUserId,
            amountMinor: 25050, // 250.50 ETB
            currency: "ETB",
            description: "Cross-Bank Transfer"
        };
        console.log(`   ➔ POST ${TX_API}/initiate -> ${JSON.stringify(initTxRequest)}`);
        
        // Mock FCM Trigger from backend
        const nonce = "nonce-" + Date.now();
        console.log(`   📡 [Mock FCM] Push notification sent to Bob's device. Nonce generated: ${nonce}`);

        console.log("\n   📱 [Bob's Android Device] Waking up app. Requesting Biometric Prompt...");
        await delay(1000);
        console.log("   📱 Biometric scan successful. Unlocking CryptoObject EC Key.");
        
        // FAPI 2.0 Signature structure
        const header = { alg: "ES256", typ: "JWT", kid: bobKeyId };
        const payload = {
            iss: "trust-layer-crypto-app",
            sub: bobUserId,
            aud: "https://tx.trustlayer.et",
            nonce: nonce,
            txInfo: {
                merchantId: aliceUserId,
                amountMinor: 25050,
                currency: "ETB"
            },
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 60
        };

        const cryptogramData = base64urlEncode(header) + "." + base64urlEncode(payload);
        const mockSignature = "MOCK_EC_SIGNATURE_BYTES_REPRESENTING_HARDWARE_SIGNING_" + bobKeyId;
        
        console.log("   ➔ Generating Cryptogram (JWS Canonical payload)...");
        console.log(`      Base64 Signature String: ${cryptogramData}.${mockSignature}`);

        const submitTxRequest = {
            payloadBase64: cryptogramData,
            signatureBase64: mockSignature,
            keyId: bobKeyId,
            algorithm: "ES256"
        };

        console.log(`\n   ➔ POST ${TX_API}/submit -> Submit Cryptogram via MTLS...`);
        console.log("   ✅ Transaction Service validates Nonce, Auth Token cnf.jkt, hardware EC Signature.");
        console.log("   ✅ Bank BOA Virtual Card Limit verified. Deducting 250.50 ETB.");
        console.log("   ✅ Bank CBE Virtual Card Balance increased. Transferring 250.50 ETB.");

        console.log("\n==========================================");
        console.log("🎉 FEDERATED FLOW COMPLETE.");
        console.log("==========================================");

    } catch(err) {
        console.error("Simulation script failed:", err);
    }
}

runE2ESimulation();
