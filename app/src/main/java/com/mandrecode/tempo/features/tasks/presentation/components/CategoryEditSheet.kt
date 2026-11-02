package com.mandrecode.tempo.features.tasks.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.domain.util.ValidationUtils
import com.mandrecode.tempo.core.ui.components.ColorPicker
import com.mandrecode.tempo.core.ui.components.IconPicker
import com.mandrecode.tempo.core.ui.components.TempoModalTopSheet
import com.mandrecode.tempo.core.ui.editor.editorSheetHorizontalPadding
import com.mandrecode.tempo.core.ui.glass.glassButtonContainer
import com.mandrecode.tempo.core.ui.theme.LocalIsDarkTheme
import com.mandrecode.tempo.core.ui.theme.TempoIcon
import com.mandrecode.tempo.core.ui.theme.dialogAction
import com.mandrecode.tempo.core.ui.theme.getMaterialYouColors
import com.mandrecode.tempo.core.ui.theme.getMonochromeColor
import com.mandrecode.tempo.core.ui.theme.getPastelColors
import com.mandrecode.tempo.core.ui.theme.inputTitle
import com.mandrecode.tempo.core.ui.theme.resolveColor
import com.mandrecode.tempo.core.ui.theme.resolveColorToKey
import com.mandrecode.tempo.core.ui.util.rememberPressableButtonAnimation
import com.mandrecode.tempo.core.ui.util.rememberPressableIconButtonAnimation
import com.mandrecode.tempo.core.ui.util.selectRandomColor
import com.mandrecode.tempo.features.tasks.domain.model.Category

internal const val CATEGORY_EDIT_SHEET_NAME_FIELD_TEST_TAG = "category_edit_sheet_name_field"

@Composable
fun CategoryEditSheet(
    category: Category?,
    categories: List<Category>,
    nameError: Int?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String?, icon: String?, isDefault: Boolean, sectionsEnabled: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = LocalIsDarkTheme.current
    val currentOnSave by rememberUpdatedState(onSave)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnClearError by rememberUpdatedState(onClearError)

    val isEditing = category != null

    var nameField by remember(category?.id) {
        val text = category?.name ?: ""
        mutableStateOf(TextFieldValue(text, selection = TextRange(text.length)))
    }
    var selectedColorKey by remember(category?.id) { mutableStateOf(category?.color) }
    var selectedIconName by remember(category?.id) { mutableStateOf(category?.icon) }
    var selectedIsDefault by remember(category?.id) { mutableStateOf(category?.isDefault ?: false) }
    var sectionsEnabled by remember(category?.id) { mutableStateOf(category?.sectionsEnabled ?: false) }

    // Track auto-selected values to exclude from unsaved-changes detection
    var autoSelectedColor by remember(category?.id) { mutableStateOf<String?>(null) }
    var autoSelectedIcon by remember(category?.id) { mutableStateOf<String?>(null) }
    var userManuallySelectedIcon by remember(category?.id) { mutableStateOf(false) }

    // Auto-select random color for new categories
    LaunchedEffect(category?.id) {
        if (!isEditing && selectedColorKey == null) {
            val existingColors =
                categories.mapNotNull { cat ->
                    cat.color?.let { key -> resolveColor(key, colorScheme, isDarkTheme) }
                }
            val availableOptions = getMaterialYouColors(colorScheme) + getMonochromeColor() + getPastelColors()
            if (availableOptions.isNotEmpty()) {
                val selectedColor =
                    selectRandomColor(
                        availableOptions = availableOptions,
                        isDarkTheme = isDarkTheme,
                        existingColors = existingColors,
                    )
                resolveColorToKey(selectedColor, colorScheme, isDarkTheme)?.let {
                    autoSelectedColor = it
                    selectedColorKey = it
                }
            }
        }
    }

    // Pre-select "category" icon for new categories
    LaunchedEffect(category?.id) {
        if (!isEditing && selectedIconName == null) {
            autoSelectedIcon = TempoIcon.CATEGORY.iconName
            selectedIconName = TempoIcon.CATEGORY.iconName
        }
    }

    // Predictive icon selection based on name — only when icon is still auto-selected
    LaunchedEffect(nameField.text) {
        if (!isEditing && !userManuallySelectedIcon && nameField.text.isNotBlank()) {
            val suggestedIcon = TempoIcon.suggestIcon(nameField.text, context)
            if (suggestedIcon != null) {
                autoSelectedIcon = suggestedIcon.iconName
                selectedIconName = suggestedIcon.iconName
            }
        }
    }

    val hasChanges =
        remember(
            nameField.text,
            selectedColorKey,
            selectedIconName,
            selectedIsDefault,
            sectionsEnabled,
            autoSelectedColor,
            autoSelectedIcon,
        ) {
            if (isEditing) {
                nameField.text != (category?.name ?: "") ||
                    selectedColorKey != category?.color ||
                    selectedIconName != category?.icon ||
                    selectedIsDefault != (category?.isDefault ?: false) ||
                    sectionsEnabled != (category?.sectionsEnabled ?: false)
            } else {
                nameField.text.isNotBlank() ||
                    sectionsEnabled ||
                    (selectedColorKey != null && selectedColorKey != autoSelectedColor) ||
                    (selectedIconName != null && selectedIconName != autoSelectedIcon)
            }
        }

    val saveEnabled = nameField.text.isNotBlank() && (hasChanges || !isEditing)

    val nameFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }

    TempoModalTopSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        hasUnsavedChanges = hasChanges,
    ) { onRequestDismiss ->
        val focusManager = LocalFocusManager.current
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = editorSheetHorizontalPadding())
                    .padding(top = 8.dp, bottom = 32.dp),
        ) {
            TextField(
                value = nameField,
                onValueChange = {
                    if (it.text.length <= ValidationUtils.MAX_CATEGORY_NAME_LENGTH) {
                        nameField = it
                    }
                    currentOnClearError()
                },
                placeholder = {
                    Text(
                        if (isEditing) {
                            stringResource(R.string.edit_category)
                        } else {
                            stringResource(R.string.new_category)
                        },
                        style = MaterialTheme.typography.inputTitle,
                    )
                },
                textStyle = MaterialTheme.typography.inputTitle,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                        errorContainerColor = Color.Transparent,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(CATEGORY_EDIT_SHEET_NAME_FIELD_TEST_TAG)
                        .focusRequester(nameFocusRequester),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            defaultKeyboardAction(ImeAction.Done)
                            focusManager.clearFocus()
                        },
                    ),
                isError = nameError != null,
                supportingText =
                    if (nameError != null) {
                        { Text(stringResource(nameError)) }
                    } else {
                        null
                    },
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mood),
                        contentDescription = stringResource(R.string.icon_label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconPicker(
                    selectedIconName = selectedIconName,
                    onSelectIcon = {
                        selectedIconName = it
                        userManuallySelectedIcon = true
                    },
                    onClearIcon = {
                        selectedIconName = null
                        userManuallySelectedIcon = false
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_draw),
                        contentDescription = stringResource(R.string.color_label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ColorPicker(
                    selectedColorKey = selectedColorKey,
                    onSelectColorKey = { selectedColorKey = it },
                    onClearColor = { selectedColorKey = null },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            CategorySectionsToggle(
                checked = sectionsEnabled,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    sectionsEnabled = it
                },
            )

            if (isEditing) {
                Spacer(modifier = Modifier.height(24.dp))

                if (selectedIsDefault) {
                    TextButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_star),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.category_is_default),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    val (defaultInteractionSource, defaultCornerRadius) =
                        rememberPressableButtonAnimation()

                    val defaultShape = RoundedCornerShape(defaultCornerRadius.value)
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedIsDefault = true
                        },
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .height(48.dp),
                        shape = defaultShape,
                        interactionSource = defaultInteractionSource,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_star),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.set_as_default),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isEditing && onDelete != null) {
                    val (deleteInteractionSource, deleteCornerRadius) =
                        rememberPressableIconButtonAnimation()

                    val deleteShape = RoundedCornerShape(deleteCornerRadius.value)
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnDelete?.invoke()
                        },
                        shape = deleteShape,
                        modifier = Modifier.height(48.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        interactionSource = deleteInteractionSource,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_forever),
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(0.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val (cancelInteractionSource, cancelCornerRadius) =
                        rememberPressableButtonAnimation()

                    OutlinedButton(
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = glassButtonContainer()),
                        border = null,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onRequestDismiss()
                        },
                        shape = RoundedCornerShape(cancelCornerRadius.value),
                        modifier = Modifier.height(48.dp),
                        interactionSource = cancelInteractionSource,
                    ) {
                        Text(
                            stringResource(R.string.cancel),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    val (saveInteractionSource, saveCornerRadius) =
                        rememberPressableButtonAnimation()

                    val saveContainerColor by animateColorAsState(
                        targetValue =
                            if (saveEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            },
                        animationSpec = tween(200),
                        label = "save_container_color",
                    )
                    val saveContentColor by animateColorAsState(
                        targetValue =
                            if (saveEnabled) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        animationSpec = tween(200),
                        label = "save_content_color",
                    )

                    val saveShape = RoundedCornerShape(saveCornerRadius.value)
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnSave(
                                nameField.text.trim(),
                                selectedColorKey,
                                selectedIconName,
                                selectedIsDefault,
                                sectionsEnabled,
                            )
                        },
                        shape = saveShape,
                        enabled = saveEnabled,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = saveContainerColor,
                                contentColor = saveContentColor,
                                disabledContainerColor = saveContainerColor,
                                disabledContentColor = saveContentColor,
                            ),
                        interactionSource = saveInteractionSource,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Text(
                            stringResource(R.string.save),
                            style = MaterialTheme.typography.dialogAction,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Turns sections on or off for the category (#61), drawn as a settings option: a rounded card
 * with an icon, title, explanation and switch. Off by default; off hides the sections without
 * deleting them, so turning it back on brings them back as they were.
 */
@Composable
private fun CategorySectionsToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val title = stringResource(R.string.category_sections_label)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        // The sheet is surfaceContainerHigh, so the plain container tone barely separates from it;
        // Settings can use it because its cards sit on the page background.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_view_agenda),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.category_sections_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // The whole card is the control; the switch only shows its state.
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
