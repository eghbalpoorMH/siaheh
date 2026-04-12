package ir.siaheh.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.siaheh.R
import ir.siaheh.data.model.OtpChannel
import ir.siaheh.ui.theme.*
import ir.siaheh.util.isValidOtp
import ir.siaheh.util.toPersianDigits
import kotlinx.coroutines.delay

private fun messengerLabel(type: String) = when (type) {
    "bale" -> "بله"
    "eitaa" -> "ایتا"
    "rubika" -> "روبیکا"
    else -> type
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerifyScreen(
    phone: String,
    isLoading: Boolean,
    error: String?,
    otpExpiresIn: Int,
    otpChannels: List<OtpChannel> = emptyList(),
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onEditPhone: () -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var timer by remember { mutableIntStateOf(otpExpiresIn) }
    var isResending by remember { mutableStateOf(false) }

    LaunchedEffect(otpExpiresIn) {
        timer = otpExpiresIn
        while (timer > 0) {
            delay(1000)
            timer--
        }
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            isResending = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = "\uD83D\uDD12", fontSize = 48.sp)
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = stringResource(R.string.otp_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = phone.toPersianDigits(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.sm))
                TextButton(onClick = onEditPhone) {
                    Text(
                        stringResource(R.string.otp_edit_phone),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 5) { code = it; onClearError() } },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "_ _ _ _ _",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (isValidOtp(code)) onVerify(code) },
                    ),
                    singleLine = true,
                    shape = ShapeLg,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        textAlign = TextAlign.Center,
                        letterSpacing = 8.sp,
                    ),
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            Button(
                onClick = { if (isValidOtp(code)) onVerify(code) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = ShapeLg,
                enabled = isValidOtp(code) && !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = "تأیید", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            if (timer > 0) {
                Text(
                    text = stringResource(R.string.otp_resend_timer, timer.toPersianDigits()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                TextButton(
                    enabled = !isLoading && !isResending,
                    onClick = {
                        isResending = true
                        onResend()
                    },
                ) {
                    Text(stringResource(R.string.otp_resend))
                }
            }

            if (otpChannels.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xl))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(R.string.otp_no_sms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(otpChannels, key = { it.type + it.url }) { channel ->
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(channel.url))
                                )
                            },
                            shape = ShapeLg,
                        ) {
                            if (channel.type == "bale") {
                                Image(
                                    painter = painterResource(id = R.drawable.bale_logo),
                                    contentDescription = messengerLabel(channel.type),
                                    modifier = Modifier
                                        .height(20.dp)
                                        .width(45.dp),
                                )
                            } else {
                                Text(
                                    text = messengerLabel(channel.type),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
