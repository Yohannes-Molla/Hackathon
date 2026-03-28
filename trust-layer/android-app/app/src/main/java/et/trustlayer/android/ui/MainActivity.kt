package et.trustlayer.android.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import et.trustlayer.android.auth.OidcFlowManager
import et.trustlayer.android.crypto.TrustLayerKeyManager
import et.trustlayer.android.ekyc.EkycUiState
import et.trustlayer.android.ekyc.EkycViewModel
import et.trustlayer.android.tx.TransactionSigner
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var keyManager: TrustLayerKeyManager
    @Inject lateinit var transactionSigner: TransactionSigner
    @Inject lateinit var oidcFlowManager: OidcFlowManager

    private var startRouteOverride: String? = null

    companion object {
        private const val DEMO_USER_ID = "00000000-0000-0000-0000-000000000001"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeHandleOidcCallback(intent?.data)
        val txId = intent?.getStringExtra("tx_id")
        val amount = intent?.getStringExtra("amount")
        val merchant = intent?.getStringExtra("merchant")
        val currency = intent?.getStringExtra("currency")
        val startRoute = if (intent?.action == "APPROVE_TX" && txId != null && amount != null && merchant != null && currency != null) {
            "approve/$txId/$amount/$merchant/$currency"
        } else if (startRouteOverride != null) {
            startRouteOverride!!
        } else {
            "scan"
        }
        setContent {
            TrustLayerTheme {
                TrustLayerNavHost(
                    startDestination = startRoute,
                    transactionSigner = transactionSigner,
                    activity = this
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeHandleOidcCallback(intent.data)
    }

    private fun maybeHandleOidcCallback(uri: Uri?) {
        if (uri?.scheme != "trustlayer" || uri.host != "callback") {
            return
        }
        val authorizationCode = uri.getQueryParameter("code") ?: return
        lifecycleScope.launch {
            val exchanged = oidcFlowManager.exchangeCodeForTokens(DEMO_USER_ID, authorizationCode)
            if (exchanged) {
                startRouteOverride = "dashboard"
            }
        }
    }
}

@Composable
fun TrustLayerTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = Color(0xFF6366F1),
        secondary = Color(0xFF818CF8),
        background = Color(0xFF0F172A),
        surface = Color(0xFF1E293B),
        onPrimary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
    )
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

@Composable
fun TrustLayerNavHost(
    startDestination: String = "scan",
    transactionSigner: TransactionSigner,
    activity: FragmentActivity
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable("scan") { QrScanScreen(navController) }
        composable("ekyc/{sessionId}") { _ ->
            EkycScreen(navController = navController)
        }
        composable("dashboard") { DashboardScreen(navController) }
        composable("approve/{txId}/{amount}/{merchant}/{currency}") { backStackEntry ->
            val txId = backStackEntry.arguments?.getString("txId") ?: ""
            val amount = backStackEntry.arguments?.getString("amount")?.toDoubleOrNull() ?: 0.0
            val merchant = backStackEntry.arguments?.getString("merchant") ?: ""
            val currency = backStackEntry.arguments?.getString("currency") ?: "ETB"
            TransactionApprovalScreen(
                txId = txId,
                amount = amount,
                merchant = merchant,
                currency = currency,
                navController = navController,
                signer = transactionSigner,
                activity = activity
            )
        }
    }
}

// ──────────────────────────────── eKYC Flow ────────────────────────────────

@Composable
fun EkycScreen(
    navController: androidx.navigation.NavController,
    viewModel: EkycViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        is EkycUiState.ScanQrCode -> {
            ScanQrCodeStep(
                onQrCodeScanned = { sessionId, tenantId ->
                    viewModel.onQrCodeScanned(sessionId, tenantId)
                }
            )
        }
        is EkycUiState.IdCapture -> {
            IdCaptureStep(
                sessionId = current.sessionId,
                onPhotoCaptured = { uri -> viewModel.onIdCaptured(uri) }
            )
        }
        is EkycUiState.LiveSelfie -> {
            LiveSelfieStep(
                sessionId = current.sessionId,
                onSelfieCaptured = { uri -> viewModel.onSelfieCaptured(uri) }
            )
        }
        is EkycUiState.Processing -> {
            ProcessingStep(sessionId = current.sessionId)
        }
        is EkycUiState.Complete -> {
            CompleteStep(
                onDone = {
                    navController.navigate("dashboard") {
                        popUpTo("scan") { inclusive = true }
                    }
                }
            )
        }
        is EkycUiState.Error -> {
            ErrorStep(
                message = current.message,
                onStartAgain = { viewModel.restart() }
            )
        }
    }
}

@Composable
private fun ScanQrCodeStep(
    onQrCodeScanned: (sessionId: String, tenantId: String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Scan QR Code",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Point your camera at the QR code on your web dashboard",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(16.dp))
        StepIndicator(currentStep = 0)
        Spacer(Modifier.height(16.dp))
        QrScannerView(
            onQrCodeScanned = onQrCodeScanned,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IdCaptureStep(
    sessionId: String,
    onPhotoCaptured: (Uri) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "ID Document Capture",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Session: ${sessionId.take(12)}...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Position your ID card within the frame and tap the capture button",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(8.dp))
        StepIndicator(currentStep = 1)
        Spacer(Modifier.height(16.dp))
        PhotoCaptureView(
            facing = CameraSelector.LENS_FACING_BACK,
            onPhotoCaptured = onPhotoCaptured,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LiveSelfieStep(
    sessionId: String,
    onSelfieCaptured: (Uri) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Live Selfie",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Session: ${sessionId.take(12)}...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Look directly at the camera and tap the button to take a selfie",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(Modifier.height(8.dp))
        StepIndicator(currentStep = 2)
        Spacer(Modifier.height(16.dp))
        PhotoCaptureView(
            facing = CameraSelector.LENS_FACING_FRONT,
            onPhotoCaptured = onSelfieCaptured,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProcessingStep(sessionId: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepIndicator(currentStep = 3)
        Spacer(Modifier.height(48.dp))
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Processing Verification...",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "We're verifying your ID document and selfie.\nThis usually takes a few seconds.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Session: ${sessionId.take(12)}...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun CompleteStep(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StepIndicator(currentStep = 4)
        Spacer(Modifier.height(48.dp))
        Text(
            "Verification Complete",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = Color(0xFF059669)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Your identity has been successfully verified.\nYou can now proceed to the dashboard.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
        ) {
            Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ErrorStep(
    message: String,
    onStartAgain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Onboarding Failed",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
            color = Color(0xFFEF4444)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onStartAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Start Again", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    val labels = listOf("Scan QR", "ID Capture", "Selfie", "Processing", "Complete")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, _ ->
            val color = when {
                index < currentStep -> Color(0xFF059669)
                index == currentStep -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            }
            val symbol = if (index < currentStep) "\u2713" else "${index + 1}"
            Box(
                modifier = Modifier
                    .size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(symbol, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ──────────────────────────────── Standalone QR Scan Screen ────────────────────────────────

@Composable
fun QrScanScreen(navController: androidx.navigation.NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Trust Layer",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan the QR code from your web dashboard\nto begin secure identity verification.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = {
                navController.navigate("ekyc/new-session")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Start eKYC Verification", fontWeight = FontWeight.Bold)
        }
    }
}

// ──────────────────────────────── Transaction Approval ────────────────────────────────

@Composable
fun TransactionApprovalScreen(
    txId: String,
    amount: Double,
    merchant: String,
    currency: String,
    navController: androidx.navigation.NavController,
    signer: TransactionSigner,
    activity: FragmentActivity
) {
    var status by remember { mutableStateOf("Pending approval") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Approve Transaction", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        Text("Merchant: $merchant")
        Text("Amount: $currency $amount")
        Text("Tx ID: $txId", fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))
        Text(status, color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            activity.lifecycleScope.launch {
                val result = signer.signAndSubmit(
                    activity = activity,
                    userId = "00000000-0000-0000-0000-000000000001",
                    tenantId = "11111111-1111-1111-1111-111111111111",
                    payload = TransactionSigner.TransactionPayload(
                        txId = txId,
                        amount = amount,
                        currency = currency,
                        merchant = merchant
                    )
                )
                status = if (result.success) "Approved: ${result.status}" else "Failed: ${result.error}"
            }
        }) {
            Text("Approve with biometrics")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { navController.navigate("dashboard") }) {
            Text("Back to dashboard")
        }
    }
}

// ──────────────────────────────── Dashboard ────────────────────────────────

@Composable
fun DashboardScreen(navController: androidx.navigation.NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "Dashboard",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Identity Status", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Text("IAL2 Verified", color = Color(0xFF059669))
                Text("Biometric Bound (StrongBox)", color = Color(0xFF059669))
                Text("Virtual Card Provisioned", color = Color(0xFF059669))
            }
        }

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Virtual Card", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(16.dp))
                Text("**** **** **** 1102", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("EXP: 12/28", style = MaterialTheme.typography.bodySmall)
                    Text("CVV: ***", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
