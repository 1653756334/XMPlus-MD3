package com.xuleo.xmplus.md3.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.xuleo.xmplus.md3.R
import com.xuleo.xmplus.md3.ui.icons.XmIcons

/**
 * The app's text field: a rounded tonal shape with no outline while empty and at rest. Once it has focus or text it
 * becomes an outlined field, the name moving up into a gap in its top edge.
 *
 * A field given only a [placeholder] uses it as that name; given both, the [placeholder] is the example shown under
 * the [label]. A one-line field without its own [trailingIcon] gets a clear button once there is text.
 */
@Composable
fun XmTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    autoCorrect: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val name = label ?: placeholder
    val example = placeholder.takeIf { label != null }
    val singleLine = maxLines == 1
    val empty = value.isEmpty()
    // Empty and at rest it is a quiet tonal shape; once the name sits in the outline the fill clears, so the gap behind
    // the name stays clean.
    val rest = if (empty) scheme.surfaceContainerHighest else Color.Transparent
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = name?.let { { Text(it, maxLines = 1) } },
        placeholder = example?.let { { Text(it, maxLines = 1) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailingIcon ?: if (singleLine) ({
            AnimatedVisibility(
                visible = !empty && enabled,
                enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f),
                exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f),
            ) {
                IconButton(onClick = { onValueChange("") }) { Icon(XmIcons.Outlined.Cancel, stringResource(R.string.action_clear)) }
            }
        }) else null,
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = autoCorrect, keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = rest,
            disabledContainerColor = rest,
            errorContainerColor = Color.Transparent,
            unfocusedBorderColor = if (empty) Color.Transparent else scheme.outline,
            disabledBorderColor = if (empty) Color.Transparent else scheme.onSurface.copy(alpha = 0.12f),
        ),
    )
}
