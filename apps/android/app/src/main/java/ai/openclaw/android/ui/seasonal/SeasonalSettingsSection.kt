package ai.openclaw.android.ui.seasonal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SeasonalSettingsSection(
    selected: SeasonalEffect,
    onSelect: (SeasonalEffect) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Seasonal Effects",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        val options = listOf(
            SeasonalEffect.AUTO to "Changes automatically based on the time of year",
            SeasonalEffect.WINTER to "Snowflakes ❄️",
            SeasonalEffect.SPRING to "Cherry blossoms 🌸",
            SeasonalEffect.SUMMER to "Sun sparkles ☀️",
            SeasonalEffect.AUTUMN to "Falling leaves 🍂",
            SeasonalEffect.OFF to "No visual effects",
        )

        for ((effect, description) in options) {
            ListItem(
                headlineContent = { Text(effect.label) },
                supportingContent = {
                    Text(
                        text = if (effect == SeasonalEffect.AUTO) {
                            val current = SeasonalEffect.currentSeason()
                            "$description (currently: ${current.label})"
                        } else description,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingContent = {
                    RadioButton(
                        selected = selected == effect,
                        onClick = { onSelect(effect) }
                    )
                }
            )
        }
    }
}
