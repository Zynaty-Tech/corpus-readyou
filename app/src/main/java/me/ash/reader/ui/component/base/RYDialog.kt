package me.ash.reader.ui.component.base

import me.ash.reader.ui.qa.ControlPrefix
import me.ash.reader.ui.qa.overlayRoot
import me.ash.reader.ui.qa.overlayTagName
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

@Composable
fun RYDialog(
    modifier: Modifier = Modifier,
    visible: Boolean,
    properties: DialogProperties = DialogProperties(),
    overlayTag: String? = null,
    onDismissRequest: () -> Unit = {},
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    if (visible) {
        // mqa: the dialog window is its own root: expose tags, tag it, and name its controls after it
        val tag = overlayTag ?: overlayTagName("Dialog")
        AlertDialog(
            properties = properties,
            modifier = modifier.overlayRoot(tag),
            onDismissRequest = onDismissRequest,
            icon = icon?.let { c -> { ControlPrefix(tag) { c() } } },
            title = title?.let { c -> { ControlPrefix(tag) { c() } } },
            text = text?.let { c -> { ControlPrefix(tag) { c() } } },
            confirmButton = { ControlPrefix(tag) { confirmButton() } },
            dismissButton = dismissButton?.let { c -> { ControlPrefix(tag) { c() } } },
        )
    }
}