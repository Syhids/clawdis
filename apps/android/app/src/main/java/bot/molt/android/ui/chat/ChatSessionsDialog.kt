package bot.molt.android.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bot.molt.android.chat.ChatSessionEntry

@Composable
fun ChatSessionsDialog(
  currentSessionKey: String,
  sessions: List<ChatSessionEntry>,
  onDismiss: () -> Unit,
  onRefresh: () -> Unit,
  onSelect: (sessionKey: String) -> Unit,
) {
  var searchQuery by remember { mutableStateOf("") }

  val filteredSessions = remember(sessions, searchQuery) {
    if (searchQuery.isBlank()) {
      sessions
    } else {
      val query = searchQuery.trim().lowercase()
      sessions.filter { entry ->
        val name = (entry.displayName ?: entry.key).lowercase()
        name.contains(query)
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {},
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Sessions", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = onRefresh) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh")
        }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text("Search sessions…") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
              }
            }
          },
          singleLine = true,
        )

        if (sessions.isEmpty()) {
          Text("No sessions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (filteredSessions.isEmpty()) {
          Text("No matching sessions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filteredSessions, key = { it.key }) { entry ->
              SessionRow(
                entry = entry,
                isCurrent = entry.key == currentSessionKey,
                onClick = { onSelect(entry.key) },
              )
            }
          }
        }
      }
    },
  )
}

@Composable
private fun SessionRow(
  entry: ChatSessionEntry,
  isCurrent: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = MaterialTheme.shapes.medium,
    color =
      if (isCurrent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(entry.displayName ?: entry.key, style = MaterialTheme.typography.bodyMedium)
      Spacer(modifier = Modifier.weight(1f))
      if (isCurrent) {
        Text("Current", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
