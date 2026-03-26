package et.trustlayer.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import et.trustlayer.android.crypto.TrustLayerKeyManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var keyManager: TrustLayerKeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TrustLayerTheme {
                TrustLayerNavHost()
            }
        }
    }
}

@Composable
fun TrustLayerTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
        secondary = androidx.compose.ui.graphics.Color(0xFF818CF8),
        background = androidx.compose.ui.graphics.Color(0xFF0F172A),
        surface = androidx.compose.ui.graphics.Color(0xFF1E293B),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        onBackground = androidx.compose.ui.graphics.Color.White,
        onSurface = androidx.compose.ui.graphics.Color.White,
    )
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

@Composable
fun TrustLayerNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "scan") {
        composable("scan") { QrScanScreen(navController) }
        composable("ekyc/{sessionId}") { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            EkycScreen(sessionId, navController)
        }
        composable("dashboard") { DashboardScreen(navController) }
    }
}

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
            "🛡️",
            fontSize = 64.sp
        )
        Spacer(Modifier.height(24.dp))
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = {
                // In production: launch CameraX preview with QrCodeAnalyzer
                // For demo: navigate directly with a mock session
                navController.navigate("ekyc/demo-session-123")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text("Open Camera Scanner", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EkycScreen(sessionId: String, navController: androidx.navigation.NavController) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf("Document Capture", "3D Liveness", "Processing", "Complete")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "eKYC Verification",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Session: ${sessionId.take(12)}...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(48.dp))

        // Step indicator
        steps.forEachIndexed { index, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val color = when {
                    index < step -> MaterialTheme.colorScheme.primary
                    index == step -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
                Text(
                    "${index + 1}",
                    modifier = Modifier.width(32.dp),
                    color = color,
                    fontWeight = if (index == step) FontWeight.Black else FontWeight.Normal
                )
                Text(label, color = color)
                if (index < step) {
                    Spacer(Modifier.weight(1f))
                    Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        if (step < steps.size - 1) {
            Button(
                onClick = { step++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    if (step == 0) "Capture Document"
                    else if (step == 1) "Start Liveness Check"
                    else "Processing...",
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = { navController.navigate("dashboard") { popUpTo("scan") { inclusive = true } } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF059669))
            ) {
                Text("🎉  Verification Complete — Go to Dashboard", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DashboardScreen(navController: androidx.navigation.NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            "🛡️ Dashboard",
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
                Text("✅ IAL2 Verified", color = androidx.compose.ui.graphics.Color(0xFF059669))
                Text("✅ Biometric Bound (StrongBox)", color = androidx.compose.ui.graphics.Color(0xFF059669))
                Text("✅ Virtual Card Provisioned", color = androidx.compose.ui.graphics.Color(0xFF059669))
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
                Text("•••• •••• •••• 1102", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("EXP: 12/28", style = MaterialTheme.typography.bodySmall)
                    Text("CVV: •••", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
