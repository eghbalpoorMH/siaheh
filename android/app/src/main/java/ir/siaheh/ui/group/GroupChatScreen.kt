package ir.siaheh.ui.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.siaheh.ui.common.FriendlyErrorMapper
import ir.siaheh.ui.components.ErrorState
import ir.siaheh.ui.components.LoadingIndicator
import ir.siaheh.ui.theme.ShapeLg
import ir.siaheh.ui.theme.Spacing
import ir.siaheh.util.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    onBack: () -> Unit,
    onOpenMembers: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var senderId by remember { mutableStateOf(TextFieldValue(uiState.senderId ?: "")) }
    var dateFrom by remember { mutableStateOf(TextFieldValue(uiState.dateFrom ?: "")) }
    var dateTo by remember { mutableStateOf(TextFieldValue(uiState.dateTo ?: "")) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(groupId) { viewModel.loadMessages(groupId) }
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
                        Text(uiState.group?.title ?: "گروه", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "یادداشت‌ها و اتفاقات این گروه",
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
                        Icon(Icons.Default.Group, contentDescription = "اعضا")
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
                onValueChange = { viewModel.onSearchChange(groupId, it) },
                label = { Text("جستجو در پیام‌ها") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                singleLine = true,
            )

            val hasFilters = uiState.senderId != null || uiState.dateFrom != null || uiState.dateTo != null
            if (hasFilters) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    uiState.senderId?.takeIf { it.isNotBlank() }?.let { sender ->
                        item {
                            AssistChip(
                                onClick = { showFilterDialog = true },
                                label = { Text("فرستنده: $sender") },
                            )
                        }
                    }
                    uiState.dateFrom?.takeIf { it.isNotBlank() }?.let { from ->
                        item {
                            AssistChip(
                                onClick = { showFilterDialog = true },
                                label = { Text("از: $from") },
                            )
                        }
                    }
                    uiState.dateTo?.takeIf { it.isNotBlank() }?.let { to ->
                        item {
                            AssistChip(
                                onClick = { showFilterDialog = true },
                                label = { Text("تا: $to") },
                            )
                        }
                    }
                    item {
                        TextButton(onClick = {
                            senderId = TextFieldValue("")
                            dateFrom = TextFieldValue("")
                            dateTo = TextFieldValue("")
                            viewModel.applyFilters(groupId, null, null, null)
                        }) {
                            Text("پاک کردن فیلترها")
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
            }

            if (uiState.isLoading && uiState.messages.isEmpty()) {
                LoadingIndicator(fullScreen = true)
            } else if (uiState.messages.isEmpty()) {
                val emptyMessage = uiState.error?.let { FriendlyErrorMapper.map(it).message }
                ErrorState(
                    message = emptyMessage ?: "هنوز پیامی در این گروه ثبت نشده.",
                    actionLabel = "فیلتر پیام‌ها",
                    onRetry = { showFilterDialog = true },
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
                    items(uiState.messages, key = { it.id }) { msg ->
                        Surface(
                            shape = ShapeLg,
                            tonalElevation = 1.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(msg.senderPhone, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        formatRelativeTime(msg.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (msg.text.isNotBlank()) {
                                    Spacer(Modifier.height(Spacing.xs))
                                    Text(msg.text, style = MaterialTheme.typography.bodyMedium)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text("ثبت پیام جدید") },
                        modifier = Modifier.weight(1f),
                        maxLines = 4,
                    )
                    FilledIconButton(
                        onClick = {
                            viewModel.sendMessage(groupId, messageText.text)
                            messageText = TextFieldValue("")
                        },
                        enabled = messageText.text.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = "ارسال")
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
                        value = senderId,
                        onValueChange = { senderId = it },
                        label = { Text("شناسه فرستنده") },
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
                    viewModel.applyFilters(groupId, senderId.text, dateFrom.text, dateTo.text)
                    showFilterDialog = false
                }) { Text("اعمال") }
            },
            dismissButton = {
                TextButton(onClick = { showFilterDialog = false }) { Text("انصراف") }
            },
        )
    }
}
