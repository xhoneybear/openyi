package dev.lisek.openyi

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun RtspPreview(
    url: String,
    restartToken: Int = 0,
    modifier: Modifier = Modifier,
    onStateChanged: (RtspPlayerState) -> Unit = {}
)

enum class RtspPlayerState {
    Idle,
    Buffering,
    Playing,
    Error
}
