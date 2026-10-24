package dev.lisek.openyi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun RtspPreview(
    url: String,
    restartToken: Int,
    modifier: Modifier,
    onStateChanged: (RtspPlayerState) -> Unit
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text("RTSP video playback is not available on iOS yet")
    }
}
