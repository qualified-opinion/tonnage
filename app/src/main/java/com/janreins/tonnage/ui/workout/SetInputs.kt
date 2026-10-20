package com.janreins.tonnage.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.janreins.tonnage.domain.SetInput

enum class CellKind(val keyboardType: KeyboardType, val input: SetInput.Kind) {
    WEIGHT(KeyboardType.Decimal, SetInput.Kind.WEIGHT),
    REPS(KeyboardType.Number, SetInput.Kind.REPS),
    DISTANCE(KeyboardType.Decimal, SetInput.Kind.DISTANCE),
    DURATION(KeyboardType.Number, SetInput.Kind.DURATION),
}

/**
 * A compact number box for a set. It keeps its own text while you type (so "62." isn't
 * reformatted mid-entry) and reports every change; [key] resets it for a different set.
 * When it isn't focused it follows [initial], so values filled in from elsewhere show up, and
 * leaving it shows the saved value again (a time typed as "130" then reads "1:30"). A new
 * [fill] (tapping "previous" or ticking a set) replaces text even while the box is focused.
 */
/** Text pushed into a cell from outside; [id] changes on every push so the same text re-applies. */
data class CellFill(val id: Int, val text: String)

@Composable
fun SetNumberCell(
    key: Any,
    initial: String,
    placeholder: String,
    kind: CellKind,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fill: CellFill? = null,
) {
    var text by remember(key) { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    val saved by rememberUpdatedState(initial)
    LaunchedEffect(initial) {
        if (!focused) text = initial
    }
    LaunchedEffect(fill) {
        fill?.let { text = it.text }
    }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = text,
        onValueChange = { input ->
            SetInput.accept(kind.input, text, input)?.let { accepted ->
                if (accepted != text) {
                    text = accepted
                    onChange(accepted)
                }
            }
        },
        modifier = modifier.height(40.dp).onFocusChanged {
            if (focused && !it.isFocused) text = saved
            focused = it.isFocused
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = kind.keyboardType, imeAction = ImeAction.Next),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
                inner()
            }
        },
    )
}
