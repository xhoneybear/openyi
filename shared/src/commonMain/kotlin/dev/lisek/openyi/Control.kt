package dev.lisek.openyi

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.roundedfilled.Photo_camera
import com.composables.icons.materialsymbols.roundedfilled.Settings
import com.composables.icons.materialsymbols.roundedfilled.Timer_10_alt_1
import com.composables.icons.materialsymbols.roundedfilled.Timer_3_alt_1
import com.composables.icons.materialsymbols.roundedfilled.Timer_5
import com.composables.icons.materialsymbols.roundedfilled.Timer_off
import com.composables.icons.materialsymbols.roundedfilled.Videocam

enum class TimerDuration(val duration: Int) {
    OFF(0),
    S_3(3000),
    S_5(5000),
    S_10(10000);
    
    fun next(): TimerDuration {
        return when (this) {
            OFF -> S_3
            S_3 -> S_5
            S_5 -> S_10
            S_10 -> OFF
        }
    }

    fun toIcon(): androidx.compose.ui.graphics.vector.ImageVector {
        return when (this) {
            OFF -> MaterialSymbols.RoundedFilled.Timer_off
            S_3 -> MaterialSymbols.RoundedFilled.Timer_3_alt_1
            S_5 -> MaterialSymbols.RoundedFilled.Timer_5
            S_10 -> MaterialSymbols.RoundedFilled.Timer_10_alt_1
        }
    }
}

@Composable
fun Control() {
    var playerState by remember { mutableStateOf(RtspPlayerState.Idle) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var restartToken by remember { mutableStateOf(0) }
    var hasStarted by remember { mutableStateOf(false) }
    var recordMode by remember { mutableStateOf(false) }
    var timer by remember { mutableStateOf(TimerDuration.OFF) }

    Scaffold(
        topBar = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .windowInsetsPadding(
                        WindowInsets
                            .safeContent
                            .only(WindowInsetsSides.Top)
                    )
            ) {
                Row(Modifier.weight(1f)) {}
                Text(
                    text = "OpenYi",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Row (
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { /* Handle settings click */ }
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.RoundedFilled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets
                            .safeContent
                            .only(WindowInsetsSides.Bottom)
                    )
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .clickable { recordMode = !recordMode }
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.RoundedFilled.Photo_camera,
                            contentDescription = "Photo Mode",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .background(
                                    if (recordMode) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    CircleShape
                                )
                                .padding(8.dp)
                        )
                        Icon(
                            imageVector = MaterialSymbols.RoundedFilled.Videocam,
                            contentDescription = "Video Mode",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .background(
                                    if (recordMode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    CircleShape
                                )
                                .padding(8.dp)
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .aspectRatio(1f)
                        .border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                        .padding(8.dp)
                        .weight(1f)
                ) {
                    Button(
                        modifier = Modifier.fillMaxSize(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (recordMode) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        ),
                        shape = CircleShape,
                        onClick = { /* Handle capture click */ }
                    ) {}
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { timer = timer.next() }
                    ) {
                        Icon(
                            imageVector = timer.toIcon(),
                            contentDescription = "Timer",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RtspPreview(
                url = "rtsp://192.168.42.1:554/live",
                restartToken = restartToken,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                onStateChanged = { state ->
                    playerState = state
                    when (state) {
                        RtspPlayerState.Buffering,
                        RtspPlayerState.Playing -> {
                            hasStarted = true
                            errorMessage = null
                        }
                        RtspPlayerState.Idle,
                        RtspPlayerState.Error -> {
                            errorMessage = if (state == RtspPlayerState.Error) {
                                "Unable to decode the RTSP stream"
                            } else {
                                null
                            }
                            if (hasStarted) {
                                hasStarted = false
                                restartToken += 1
                            }
                        }
                    }
                }
            )
            Text(playerState.label)
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private val RtspPlayerState.label: String
    get() = when (this) {
        RtspPlayerState.Idle -> "Preview idle"
        RtspPlayerState.Buffering -> "Buffering stream..."
        RtspPlayerState.Playing -> "Playing"
        RtspPlayerState.Error -> "Preview error"
    }
