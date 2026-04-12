package ir.siaheh.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ir.siaheh.ui.auth.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { if (!updateInfo.isForced) onDismiss() },
        title = {
            Text(
                if (updateInfo.isForced) "به‌روزرسانی اجباری"
                else "نسخه جدید موجود است"
            )
        },
        text = {
            Text(
                if (updateInfo.isForced)
                    "برای ادامه استفاده از برنامه باید به نسخه ${updateInfo.latestVersion} به‌روزرسانی کنید."
                else
                    "نسخه ${updateInfo.latestVersion} منتشر شده. پیشنهاد می‌کنیم به‌روزرسانی کنید."
            )
        },
        confirmButton = {
            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.updateUrl)))
            }) {
                Text("به‌روزرسانی")
            }
        },
        dismissButton = if (!updateInfo.isForced) {{
            TextButton(onClick = onDismiss) {
                Text("بعداً")
            }
        }} else null,
    )
}
