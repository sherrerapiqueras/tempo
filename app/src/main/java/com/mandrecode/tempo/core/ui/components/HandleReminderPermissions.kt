// The two permission constants lint flags are compile-time strings, inlined into older builds: the
// notification request only runs where hasNotificationPermission() can be false (Android 13+), and
// the exact-alarm settings screen only once canScheduleExactAlarms() said no (Android 12+).
@file:SuppressLint("InlinedApi")

package com.mandrecode.tempo.core.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.glass.DialogWindowGlass

@Composable
fun HandleReminderPermissions(
    show: Boolean,
    onGrantPermissions: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showEducationDialog by remember { mutableStateOf(false) }
    var showRationaleDialogRes by remember { mutableIntStateOf(0) }
    var isPermanentlyDeclined by remember { mutableStateOf(false) }
    var shouldCheckExactAlarm by remember { mutableStateOf(false) }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                shouldCheckExactAlarm = true
            } else {
                isPermanentlyDeclined = activity?.let {
                    !it.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                } ?: false

                showRationaleDialogRes =
                    if (isPermanentlyDeclined) {
                        R.string.permanently_disabled_notifications_rationale
                    } else {
                        R.string.notification_permission_rationale
                    }
            }
        }

    val exactAlarmPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) {
            // Returned from settings, re-check and act
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                if (alarmManager.canScheduleExactAlarms()) {
                    onGrantPermissions()
                } else {
                    onDismiss()
                }
            }
        }

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect

        if (hasNotificationPermission()) {
            shouldCheckExactAlarm = true
        } else {
            showEducationDialog = true
        }
    }

    DisposableEffect(lifecycleOwner, showEducationDialog) {
        if (!showEducationDialog) {
            return@DisposableEffect onDispose { }
        }

        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && hasNotificationPermission()) {
                    showEducationDialog = false
                    shouldCheckExactAlarm = true
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (shouldCheckExactAlarm) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                onGrantPermissions()
                shouldCheckExactAlarm = false
            } else {
                showRationaleDialogRes = R.string.exact_alarm_permission_rationale
            }
        } else {
            onGrantPermissions()
            shouldCheckExactAlarm = false
        }
    }

    if (showEducationDialog) {
        PermissionEducationDialog(
            onConfirm = {
                showEducationDialog = false
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onDismiss = {
                showEducationDialog = false
                onDismiss()
            },
        )
    }

    if (showRationaleDialogRes != 0) {
        PermissionRationaleDialog(
            textRes = showRationaleDialogRes,
            onConfirm = {
                val rationaleRes = showRationaleDialogRes
                showRationaleDialogRes = 0
                val needsAlarmPermission = rationaleRes == R.string.exact_alarm_permission_rationale

                if (isPermanentlyDeclined || needsAlarmPermission) {
                    val intent =
                        when {
                            needsAlarmPermission -> {
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            }

                            isPermanentlyDeclined && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            }

                            else -> {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            }
                        }

                    if (needsAlarmPermission) {
                        exactAlarmPermissionLauncher.launch(intent)
                    } else {
                        context.startActivity(intent)
                        onDismiss()
                    }
                } else {
                    onDismiss()
                }
            },
            onDismiss = {
                showRationaleDialogRes = 0
                onDismiss()
            },
        )
    }
}

@Composable
internal fun PermissionEducationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogWindowGlass()
            Text(stringResource(R.string.notification_permission_education_title))
        },
        text = { Text(stringResource(R.string.notification_permission_education_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.enable_reminders))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.not_now))
            }
        },
    )
}

@Composable
internal fun PermissionRationaleDialog(
    textRes: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogWindowGlass()
            Text(stringResource(R.string.permission_required))
        },
        text = { Text(stringResource(textRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
