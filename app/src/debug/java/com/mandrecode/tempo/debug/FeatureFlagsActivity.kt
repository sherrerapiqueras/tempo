package com.mandrecode.tempo.debug

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.MainActivity
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.data.preferences.FeatureFlagOverrides
import com.mandrecode.tempo.core.domain.model.FeatureFlag
import com.mandrecode.tempo.core.ui.theme.TempoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Debug builds only (src/debug): switches each [FeatureFlag] on or off over its build value, so a
 * debug build can show what a release build will, without building one. Overrides are read when
 * Tempo starts, hence the restart button.
 */
@AndroidEntryPoint
class FeatureFlagsActivity : ComponentActivity() {
    @Inject
    lateinit var overrides: FeatureFlagOverrides

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TempoTheme(useTempoColors = true) {
                FeatureFlagsScreen(overrides = overrides, onRestart = ::restartTempo)
            }
        }
    }

    private fun restartTempo() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        Process.killProcess(Process.myPid())
    }
}

@Composable
private fun FeatureFlagsScreen(
    overrides: FeatureFlagOverrides,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bumped on every change, so the rows re-read the overrides they show.
    var revision by remember { mutableIntStateOf(0) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.debug_feature_flags_title), style = MaterialTheme.typography.headlineMedium)
            FeatureFlag.entries.forEach { flag ->
                FeatureFlagRow(
                    flag = flag,
                    buildValue = overrides.buildValue(flag),
                    override = revision.let { overrides.override(flag) },
                    onOverride = { value ->
                        overrides.setOverride(flag, value)
                        revision++
                    },
                )
            }
            Text(
                text = stringResource(R.string.debug_feature_flags_restart_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRestart) { Text(stringResource(R.string.debug_feature_flags_restart)) }
        }
    }
}

@Composable
private fun FeatureFlagRow(
    flag: FeatureFlag,
    buildValue: Boolean,
    override: Boolean?,
    onOverride: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val built =
        stringResource(if (buildValue) R.string.debug_feature_flags_on else R.string.debug_feature_flags_off)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = flag.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = flag.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.debug_feature_flags_build_value, flag.activationIssue, built),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (override != null) {
                TextButton(onClick = { onOverride(null) }) {
                    Text(
                        stringResource(R.string.debug_feature_flags_overridden) + " · " +
                            stringResource(R.string.debug_feature_flags_reset),
                    )
                }
            }
        }
        Switch(checked = override ?: buildValue, onCheckedChange = { onOverride(it) })
    }
}
