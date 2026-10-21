package com.cashbuddy.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(
    mimeType: String,
    onFilePicked: (content: String?, fileName: String?) -> Unit
): () -> Unit {
    return {}
}
