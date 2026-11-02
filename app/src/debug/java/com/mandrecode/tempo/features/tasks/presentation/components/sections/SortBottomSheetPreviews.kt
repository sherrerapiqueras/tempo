package com.mandrecode.tempo.features.tasks.presentation.components.sections

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import com.mandrecode.tempo.features.tasks.presentation.model.SortOption

@Preview(name = "Light", showBackground = true, device = "id:pixel_9")
@Preview(
    name = "Dark",
    showBackground = true,
    device = "id:pixel_9",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SortBottomSheetItemsPreview() {
    TempoTheme {
        Surface {
            Column(
                verticalArrangement = Arrangement.spacedBy(TasksSheetRowSpacing),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
            ) {
                SortOption.entries.forEach { sortOption ->
                    SortOptionItem(
                        sortOption = sortOption,
                        isSelected = sortOption == SortOption.BY_DATE,
                        onClick = {},
                    )
                }
            }
        }
    }
}
