package ir.siaheh.ui.space

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
import ir.siaheh.data.model.Space
import ir.siaheh.ui.common.FriendlyErrorMapper
import ir.siaheh.ui.components.ErrorState
import ir.siaheh.ui.components.LoadingIndicator
import ir.siaheh.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceListScreen(
    onOpenSpace: (String) -> Unit,
    viewModel: SpaceListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var query by remember { mutableStateOf("") }
    var pendingConvertSpace by remember { mutableStateOf<Space?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        val friendly = FriendlyErrorMapper.map(error)
        snackbarHostState.showSnackbar(friendly.message)
        viewModel.clearError()
    }

    val filteredSpaces = remember(uiState.spaces, query) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            uiState.spaces
        } else {
            uiState.spaces.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                    it.description.contains(trimmed, ignoreCase = true)
            }
        }
    }
    val pinnedSpaces = filteredSpaces.filter { it.isPinned && !it.isHidden }
    val regularSpaces = filteredSpaces.filter { !it.isPinned && !it.isHidden }
    val hiddenSpaces = filteredSpaces.filter { it.isHidden }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("فضاها", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "سیاهه‌های اشتراکی و شخصی",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSpaces() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "بارگذاری دوباره")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "ایجاد فضا")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading && uiState.spaces.isEmpty()) {
            LoadingIndicator(fullScreen = true)
            return@Scaffold
        }

        if (!uiState.isLoading && filteredSpaces.isEmpty()) {
            val emptyMessage = uiState.error?.let { FriendlyErrorMapper.map(it).message }
            ErrorState(
                message = emptyMessage ?: "هنوز فضایی ساخته نشده. اولین سیاهه را بسازیم؟",
                actionLabel = "ساخت فضا",
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
                    label = { Text("جستجو در فضاها") },
                )
            }

            if (pinnedSpaces.isNotEmpty()) {
                item {
                    Text(
                        "پین‌شده",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(pinnedSpaces, key = { it.id }) { space ->
                    SpaceCard(
                        space = space,
                        onOpen = { onOpenSpace(space.id) },
                        onTogglePin = { viewModel.updatePreferences(space.id, isPinned = !space.isPinned) },
                        onToggleHide = { viewModel.updatePreferences(space.id, isHidden = !space.isHidden) },
                        onConvertPersonal = { pendingConvertSpace = space },
                    )
                }
            }

            if (regularSpaces.isNotEmpty()) {
                item {
                    Text(
                        "فضاها",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(regularSpaces, key = { it.id }) { space ->
                    SpaceCard(
                        space = space,
                        onOpen = { onOpenSpace(space.id) },
                        onTogglePin = { viewModel.updatePreferences(space.id, isPinned = !space.isPinned) },
                        onToggleHide = { viewModel.updatePreferences(space.id, isHidden = !space.isHidden) },
                        onConvertPersonal = { pendingConvertSpace = space },
                    )
                }
            }

            if (hiddenSpaces.isNotEmpty()) {
                item {
                    Text(
                        "فضاهای مخفی",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(hiddenSpaces, key = { it.id }) { space ->
                    SpaceCard(
                        space = space,
                        onOpen = { onOpenSpace(space.id) },
                        onTogglePin = { viewModel.updatePreferences(space.id, isPinned = !space.isPinned) },
                        onToggleHide = { viewModel.updatePreferences(space.id, isHidden = !space.isHidden) },
                        onConvertPersonal = { pendingConvertSpace = space },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("ایجاد فضا") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("نام فضا") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("توضیح کوتاه") },
                        supportingText = { Text("اختیاری") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createSpace(title.text, description.text, null)
                    title = TextFieldValue("")
                    description = TextFieldValue("")
                    showCreateDialog = false
                }) { Text("ساخت") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("انصراف") }
            },
        )
    }

    pendingConvertSpace?.let { space ->
        AlertDialog(
            onDismissRequest = { pendingConvertSpace = null },
            title = { Text("تبدیل فضا شخصی") },
            text = { Text("بعد از تبدیل فضا شخصی به فضا عمومی، این تغییر برگشت‌پذیر نیست. ادامه می‌دهی؟") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.convertPersonalSpace(space.id)
                    pendingConvertSpace = null
                }) { Text("تبدیل") }
            },
            dismissButton = { TextButton(onClick = { pendingConvertSpace = null }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun SpaceCard(
    space: Space,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHide: () -> Unit,
    onConvertPersonal: () -> Unit,
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
                    Text(space.title, style = MaterialTheme.typography.titleMedium)
                    if (space.description.isNotBlank()) {
                        Text(
                            text = space.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (space.latestEntryPreview.isNotBlank()) {
                        Text(
                            text = space.latestEntryPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = if (space.isPinned) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "پین",
                            tint = if (space.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onToggleHide) {
                        Icon(
                            imageVector = if (space.isHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = "مخفی",
                            tint = if (space.isHidden) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    label = { Text("اعضا ${space.membersCount}") },
                )
                if (space.isPinned) {
                    AssistChip(onClick = {}, label = { Text("پین‌شده") })
                }
                if (space.isHidden) {
                    AssistChip(onClick = {}, label = { Text("مخفی") })
                }
                if (space.kind == "personal") {
                    AssistChip(onClick = onConvertPersonal, label = { Text("شخصی (تبدیل)") })
                }
            }
        }
    }
}
