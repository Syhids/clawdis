package ai.openclaw.android.ui.intent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.openclaw.android.intent.ConfirmationResult
import ai.openclaw.android.intent.PendingIntentConfirmation

@Composable
fun IntentConfirmationDialog(
  confirmation: PendingIntentConfirmation,
  onResult: (ConfirmationResult) -> Unit,
) {
  AlertDialog(
    onDismissRequest = { onResult(ConfirmationResult.DENY) },
    title = { Text("App Action") },
    text = {
      Column {
        if (confirmation.appName != null) {
          Text(
            confirmation.appName,
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(4.dp))
        }
        if (confirmation.packageName != null) {
          Text(
            confirmation.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
          confirmation.actionPreview,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onResult(ConfirmationResult.ALLOW_ALWAYS) }) {
          Text("Always")
        }
        Button(onClick = { onResult(ConfirmationResult.ALLOW) }) {
          Text("Allow")
        }
      }
    },
    dismissButton = {
      OutlinedButton(onClick = { onResult(ConfirmationResult.DENY) }) {
        Text("Deny")
      }
    },
  )
}
