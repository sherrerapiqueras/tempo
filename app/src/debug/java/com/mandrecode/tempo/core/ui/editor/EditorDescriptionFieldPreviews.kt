package com.mandrecode.tempo.core.ui.editor

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.core.ui.theme.TempoTheme

@Preview(name = "Placeholder", showBackground = true, widthDp = 300)
@Preview(name = "Placeholder dark", showBackground = true, widthDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditorDescriptionFieldPlaceholderPreview() {
    TempoTheme {
        EditorDescriptionField(
            value = TextFieldValue(),
            onValueChange = {},
            error = null,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Error", showBackground = true, widthDp = 300)
@Preview(name = "Error dark", showBackground = true, widthDp = 300, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EditorDescriptionFieldErrorPreview() {
    TempoTheme {
        EditorDescriptionField(
            value = TextFieldValue("Check the trail conditions and pack the bag the day before."),
            onValueChange = {},
            error = "Description is too long. Keep it short!",
            modifier = Modifier.padding(16.dp),
        )
    }
}
