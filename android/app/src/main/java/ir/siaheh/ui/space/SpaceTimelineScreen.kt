package ir.siaheh.ui.space

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.net.toUri
import coil.compose.AsyncImage
import ir.siaheh.data.model.EntryAttachment
import ir.siaheh.ui.common.FriendlyErrorMapper
import ir.siaheh.ui.components.ErrorState
import ir.siaheh.ui.components.LoadingIndicator
import ir.siaheh.ui.theme.PrimaryContainer
import ir.siaheh.ui.theme.ShapeLg
import ir.siaheh.ui.theme.Spacing
import ir.siaheh.util.formatRelativeTime

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SpaceChatScreen(
    spaceId: String,
    onBack: () -> Unit,
    onOpenMembers: () -> Unit,
    viewModel: SpaceChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var senderUsername by remember { mutableStateOf(TextFieldValue(uiState.senderUsername ?: "")) }
    var dateFrom by remember { mutableStateOf(TextFieldValue(uiState.dateFrom ?: "")) }
    var dateTo by remember { mutableStateOf(TextFieldValue(uiState.dateTo ?: "")) }
    val selectedAttachments = remember { mutableStateListOf<ComposerAttachment>() }
    val snackbarHostState = remember { SnackbarHostState() }
    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        selectedAttachments.clear()
        selectedAttachments.addAll(
            uris.map { uri ->
                ComposerAttachment(
                    uri = uri,
                    kind = detectAttachmentKind(context, uri),
                )
            }
        )
    }

    LaunchedEffect(spaceId) { viewModel.loadMessages(spaceId) }
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        val friendly = FriendlyErrorMapper.map(error)
        snackbarHostState.showSnackbar(friendly.message)
        viewModel.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.space?.title ?: "فضا", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = if (uiState.space?.kind == "personal") "ثبت‌های شخصی شما" else "تاریخچه رویدادهای فضا",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") } },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterAlt, contentDescription = "فیلتر")
                    }
                    IconButton(onClick = onOpenMembers) {
                        Icon(Icons.Default.People, contentDescription = "اعضا")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = uiState.search,
                onValueChange = { viewModel.onSearchChange(spaceId, it) },
                label = { Text("جستجو در ثبت‌ها") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                singleLine = true,
            )

            val hasFilters = uiState.senderUsername != null || uiState.dateFrom != null || uiState.dateTo != null
            if (hasFilters) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    uiState.senderUsername?.takeIf { it.isNotBlank() }?.let { sender ->
                        item { AssistChip(onClick = {}, label = { Text("فرستنده: @$sender") }) }
                    }
                    uiState.dateFrom?.takeIf { it.isNotBlank() }?.let { d ->
                        item { AssistChip(onClick = {}, label = { Text("از: $d") }) }
                    }
                    uiState.dateTo?.takeIf { it.isNotBlank() }?.let { d ->
                        item { AssistChip(onClick = {}, label = { Text("تا: $d") }) }
                    }
                }
            }

            if (uiState.isLoading && uiState.entries.isEmpty()) {
                LoadingIndicator(fullScreen = true)
            } else if (!uiState.isLoading && uiState.entries.isEmpty()) {
                ErrorState(
                    message = "هنوز رویدادی ثبت نشده.",
                    actionLabel = "تلاش مجدد",
                    onRetry = { viewModel.loadMessages(spaceId) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        val isMine = entry.senderId == uiState.currentUserId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                        ) {
                            if (!isMine && (uiState.space?.membersCount ?: 0) > 1) {
                                AsyncImage(
                                    model = entry.sender.avatarUrl,
                                    contentDescription = "avatar",
                                    modifier = Modifier
                                        .padding(end = Spacing.xs, top = Spacing.xs)
                                        .size(28.dp)
                                        .clip(ShapeLg),
                                )
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(0.88f),
                                shape = ShapeLg,
                                tonalElevation = 1.dp,
                                color = if (isMine) PrimaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(modifier = Modifier.padding(Spacing.md)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            if (entry.sender.displayName.isNotBlank()) entry.sender.displayName else "@${entry.sender.username}",
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            formatRelativeTime(entry.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (entry.text.isNotBlank()) {
                                        Spacer(Modifier.height(Spacing.xs))
                                        Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (entry.attachments.isNotEmpty()) {
                                        Spacer(Modifier.height(Spacing.sm))
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                            items(entry.attachments, key = { it.id }) { file ->
                                                AttachmentChip(file = file, onOpen = {
                                                    runCatching {
                                                        val intent = Intent(Intent.ACTION_VIEW, file.fileUrl.toUri())
                                                        context.startActivity(intent)
                                                    }
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (selectedAttachments.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            items(selectedAttachments, key = { it.uri.toString() }) { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                    if (item.kind == "image") {
                                        AsyncImage(
                                            model = item.uri,
                                            contentDescription = "preview",
                                            modifier = Modifier.size(56.dp),
                                        )
                                    }
                                    if (item.kind == "music" || item.kind == "voice") {
                                        AssistChip(
                                            onClick = {
                                                item.kind = if (item.kind == "music") "voice" else "music"
                                            },
                                            label = { Text("نوع: ${kindLabel(item.kind)}") },
                                        )
                                    }
                                    AssistChip(
                                        onClick = { selectedAttachments.remove(item) },
                                        label = {
                                            Text(
                                                item.uri.lastPathSegment?.take(20) ?: "attachment",
                                                maxLines = 1,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (uiState.isSending) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            label = { Text("ثبت رویداد جدید") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                        )
                        IconButton(onClick = { pickFilesLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.Attachment, contentDescription = "افزودن فایل")
                        }
                        FilledIconButton(
                            onClick = {
                                viewModel.sendEntry(
                                    spaceId = spaceId,
                                    text = messageText.text,
                                    attachments = selectedAttachments.map { it.uri },
                                    attachmentKinds = selectedAttachments.map { it.kind },
                                )
                                messageText = TextFieldValue("")
                                selectedAttachments.clear()
                            },
                            enabled = !uiState.isSending && (messageText.text.isNotBlank() || selectedAttachments.isNotEmpty()),
                        ) {
                            Icon(Icons.Outlined.Send, contentDescription = "ارسال")
                        }
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("فیلترها") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = senderUsername,
                        onValueChange = { senderUsername = it },
                        label = { Text("نام کاربری فرستنده") },
                    )
                    OutlinedTextField(
                        value = dateFrom,
                        onValueChange = { dateFrom = it },
                        label = { Text("از تاریخ (YYYY-MM-DD یا ISO)") },
                    )
                    OutlinedTextField(
                        value = dateTo,
                        onValueChange = { dateTo = it },
                        label = { Text("تا تاریخ (YYYY-MM-DD یا ISO)") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyFilters(spaceId, senderUsername.text, dateFrom.text, dateTo.text)
                    showFilterDialog = false
                }) { Text("اعمال") }
            },
            dismissButton = {
                TextButton(onClick = { showFilterDialog = false }) { Text("انصراف") }
            },
        )
    }
}

@Composable
private fun AttachmentChip(file: EntryAttachment, onOpen: () -> Unit) {
    AssistChip(
        onClick = onOpen,
        label = {
            Text(
                when {
                    file.originalName.isNotBlank() -> file.originalName
                    else -> kindLabel(file.kind)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private data class ComposerAttachment(
    val uri: Uri,
    var kind: String,
)

private fun detectAttachmentKind(context: android.content.Context, uri: Uri): String {
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
    return when {
        mime.startsWith("image/") -> "image"
        mime.startsWith("video/") -> "video"
        mime.startsWith("audio/") -> "music"
        mime == "application/pdf" || mime.startsWith("text/") -> "document"
        else -> "file"
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "image" -> "تصویر"
    "video" -> "ویدیو"
    "music" -> "موزیک"
    "voice" -> "ویس"
    "document" -> "سند"
    else -> "فایل"
}
