package ai.openclaw.android.ui.intent

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ai.openclaw.android.MainViewModel

fun LazyListScope.intentSettingsSection(viewModel: MainViewModel) {
  item { HorizontalDivider() }
  item { Text("App Actions & Intents", style = MaterialTheme.typography.titleSmall) }
  item {
    val enabled by viewModel.intentBridgeEnabled.collectAsState()
    ListItem(
      headlineContent = { Text("Intent Bridge") },
      supportingContent = { Text("Allow the agent to launch apps and actions on this device.") },
      trailingContent = {
        Switch(
          checked = enabled,
          onCheckedChange = viewModel::setIntentBridgeEnabled,
        )
      },
    )
  }
  item {
    val whitelistSize by viewModel.intentWhitelistSize.collectAsState()
    ListItem(
      headlineContent = { Text("Trusted Actions") },
      supportingContent = {
        Text(
          if (whitelistSize > 0) {
            "$whitelistSize action${if (whitelistSize == 1) "" else "s"} set to auto-allow."
          } else {
            "No actions set to auto-allow yet."
          },
        )
      },
      trailingContent = {
        if (whitelistSize > 0) {
          Button(onClick = viewModel::clearIntentWhitelist) {
            Text("Clear")
          }
        }
      },
    )
  }
  item {
    Text(
      "Level 1 actions (view, dial, settings) execute automatically. Level 2 actions show a confirmation dialog.",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
