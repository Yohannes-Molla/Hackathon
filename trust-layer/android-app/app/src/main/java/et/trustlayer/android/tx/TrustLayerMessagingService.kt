package et.trustlayer.android.tx

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import et.trustlayer.android.ui.MainActivity

/**
 * Phase 2F — FCM Push Handler
 *
 * Receives Firebase Cloud Messaging data messages for transaction approval:
 *   { "tx_id": "...", "amount": "1200.50", "merchant": "Pharmacy", "currency": "ETB" }
 *
 * Displays a high-priority notification with an "Approve" action that
 * deep-links into the app for biometric signing.
 */
@AndroidEntryPoint
class TrustLayerMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "TLMessagingService"
        private const val CHANNEL_ID = "tx_approval_channel"
        private const val CHANNEL_NAME = "Transaction Approvals"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed: ${token.take(10)}...")
        // In production: POST token to backend for device registration
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val txId = data["tx_id"] ?: return
        val amount = data["amount"] ?: "0.00"
        val merchant = data["merchant"] ?: "Unknown"
        val currency = data["currency"] ?: "ETB"

        Log.i(TAG, "Received tx approval request: txId=$txId, $currency $amount @ $merchant")

        showApprovalNotification(txId, amount, merchant, currency)
    }

    private fun showApprovalNotification(
        txId: String,
        amount: String,
        merchant: String,
        currency: String
    ) {
        createNotificationChannel()

        // Deep-link intent to open app at transaction approval screen
        val approveIntent = Intent(this, MainActivity::class.java).apply {
            action = "APPROVE_TX"
            putExtra("tx_id", txId)
            putExtra("amount", amount)
            putExtra("merchant", merchant)
            putExtra("currency", currency)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            txId.hashCode(),
            approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🛡️ Approve Transaction")
            .setContentText("$currency $amount at $merchant")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("Transaction Approval Required")
                    .bigText("Confirm payment of $currency $amount to $merchant.\nTap to approve with biometric.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_lock_idle_lock,
                "Approve",
                pendingIntent
            )
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(txId.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Transaction approval push notifications"
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
