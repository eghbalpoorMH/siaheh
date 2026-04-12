package ir.siaheh.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import ir.siaheh.data.model.Group
import ir.siaheh.ui.common.FriendlyErrorMapper
import ir.siaheh.ui.components.ErrorState
import ir.siaheh.ui.components.LoadingIndicator
import ir.siaheh.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onOpenGroup: (String) -> Unit,
    viewModel: GroupListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var memberIds by remember { mutableStateOf(TextFieldValue("")) }
    var query by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        val friendly = FriendlyErrorMapper.map(error)
        snackbarHostState.showSnackbar(friendly.message)
        viewModel.clearError()
    }

    val filteredGroups = remember(uiState.groups, query) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            uiState.groups
        } else {
            uiState.groups.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.description.contains(trimmed, ignoreCase = true)
            }
        }
    }
    val pinnedGroups = filteredGroups.filter { it.isPinned && !it.isHidden }
    val regularGroups = filteredGroups.filter { !it.isPinned && !it.isHidden }
    val hiddenGroups = filteredGroups.filter { it.isHidden }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("گروه‌ها", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "سیاهه‌های اشتراکی و شخصی",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadGroups() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "بارگذاری دوباره")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "ایجاد گروه")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading && uiState.groups.isEmpty()) {
            LoadingIndicator(fullScreen = true)
            return@Scaffold
        }

        if (!uiState.isLoading && filteredGroups.isEmpty()) {
            val emptyMessage = uiState.error?.let { FriendlyErrorMapper.map(it).message }
            ErrorState(
                message = emptyMessage ?: "هنوز گروهی ساخته نشده. اولین سیاهه را بسازیم؟",
                actionLabel = "ساخت گروه",
                onRetry = { showCreateDialog = true },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("جستجو در گروه‌ها") },
                )
            }

            if (pinnedGroups.isNotEmpty()) {
                item {
                    Text(
                        "پین‌شده",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(pinnedGroups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        onOpen = { onOpenGroup(group.id) },
                        onTogglePin = { viewModel.updatePreferences(group.id, isPinned = !group.isPinned) },
                        onToggleHide = { viewModel.updatePreferences(group.id, isHidden = !group.isHidden) },
                    )
                }
            }

            if (regularGroups.isNotEmpty()) {
                item {
                    Text(
                        "گروه‌ها",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(regularGroups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        onOpen = { onOpenGroup(group.id) },
                        onTogglePin = { viewModel.updatePreferences(group.id, isPinned = !group.isPinned) },
                        onToggleHide = { viewModel.updatePreferences(group.id, isHidden = !group.isHidden) },
                    )
                }
            }

            if (hiddenGroups.isNotEmpty()) {
                item {
                    Text(
                        "گروه‌های مخفی",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(hiddenGroups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        onOpen = { onOpenGroup(group.id) },
                        onTogglePin = { viewModel.updatePreferences(group.id, isPinned = !group.isPinned) },
                        onToggleHide = { viewModel.updatePreferences(group.id, isHidden = !group.isHidden) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("ایجاد گروه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("نام گروه") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("توضیح کوتاه") },
                        supportingText = { Text("اختیاری") },
                    )
                    OutlinedTextField(
                        value = memberIds,
                        onValueChange = { memberIds = it },
                        label = { Text("شناسه اعضا") },
                        supportingText = { Text("اگر لازم است، شناسه‌ها را با کاما جدا کن") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = memberIds.text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    viewModel.createGroup(title.text, description.text, ids)
                    title = TextFieldValue("")
                    description = TextFieldValue("")
                    memberIds = TextFieldValue("")
                    showCreateDialog = false
                }) { Text("ساخت") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("انصراف") }
            },
        )
    }
}

@Composable
private fun GroupCard(
    group: Group,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHide: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium)
                    if (group.description.isNotBlank()) {
                        Text(
                            text = group.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (group.isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "پین",
                            tint = if (group.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onToggleHide) {
                        Icon(
                            imageVector = if (group.isHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = "مخفی",
                            tint = if (group.isHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("اعضا ${group.membersCount}") },
                )
                if (group.isPinned) {
                    AssistChip(onClick = {}, label = { Text("پین‌شده") })
                }
                if (group.isHidden) {
                    AssistChip(onClick = {}, label = { Text("مخفی") })
                }
            }
        }
    }
}
