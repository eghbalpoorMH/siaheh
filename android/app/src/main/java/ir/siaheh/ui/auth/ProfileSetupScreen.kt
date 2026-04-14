package ir.siaheh.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import ir.siaheh.ui.theme.Spacing

@Composable
fun ProfileSetupScreen(
    isLoading: Boolean,
    currentDisplayName: String,
    currentUsername: String,
    onSubmit: (displayName: String, username: String, about: String, avatarUri: Uri?) -> Unit,
) {
    var displayName by remember { mutableStateOf(currentDisplayName) }
    var username by remember { mutableStateOf(currentUsername) }
    var about by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { avatarUri = it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("تکمیل پروفایل", style = MaterialTheme.typography.headlineSmall)
        Text("برای دیده شدن در فضاها، نام و نام کاربری خود را تنظیم کن.")
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("نام نمایشی") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.lowercase().replace(" ", "") },
            label = { Text("نام کاربری") },
            supportingText = { Text("حداقل ۴ کاراکتر، بدون فاصله") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = about,
            onValueChange = { about = it },
            label = { Text("درباره من (اختیاری)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { pickAvatar.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null)
            Text(" انتخاب آواتار")
        }
        avatarUri?.let {
            AsyncImage(
                model = it,
                contentDescription = "avatar",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
            )
        }
        Button(
            onClick = { onSubmit(displayName, username, about, avatarUri) },
            enabled = !isLoading && displayName.isNotBlank() && username.length >= 4,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("ذخیره و ادامه")
        }
    }
}
