package ai.openclaw.android.ui.chat.forms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SeparatorFormField(field: FormField.Separator) {
  Column(modifier = Modifier.padding(vertical = 4.dp)) {
    field.title?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
      )
    }
    HorizontalDivider(
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.outlineVariant,
    )
  }
}
