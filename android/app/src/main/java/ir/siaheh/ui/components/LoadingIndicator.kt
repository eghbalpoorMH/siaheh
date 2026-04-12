package ir.siaheh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ir.siaheh.ui.theme.Spacing

@Composable
fun LoadingIndicator(message: String? = null, fullScreen: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier = modifier.then(if (fullScreen) Modifier.fillMaxSize() else Modifier).padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        if (message != null) { Spacer(Modifier.height(Spacing.md)); Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
