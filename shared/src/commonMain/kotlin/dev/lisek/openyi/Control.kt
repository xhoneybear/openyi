package dev.lisek.openyi

import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun Control() {
    Scaffold {
        Button(onClick = { /* Handle button click */ }) {
            Text("Restart intro")
        }
    }
}
