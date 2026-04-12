package ir.siaheh.ui.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.siaheh.data.model.GroupMember
import ir.siaheh.ui.common.FriendlyErrorMapper
import ir.siaheh.ui.components.ErrorState
import ir.siaheh.ui.components.LoadingIndicator
import ir.siaheh.ui.theme.ShapeLg
import ir.siaheh.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMembersScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: GroupMembersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf(TextFieldValue("")) }
    var role by remember { mutableStateOf("member") }
    var showEditDialog by remember { mutableStateOf(false) }
    var editUserId by remember { mutableStateOf("") }
    var editRole by remember { mutableStateOf("member") }
    var editCanRead by remember { mutableStateOf(true) }
    var editIsActive by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(groupId) { viewModel.loadMembers(groupId) }
    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        val friendly = FriendlyErrorMapper.map(error)
        snackbarHostState.showSnackbar(friendly.message)
        viewModel.clearError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("اعضا") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "افزودن عضو")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (uiState.isLoading && uiState.members.isEmpty()) {
            LoadingIndicator(fullScreen = true)
            return@Scaffold
        }

        if (!uiState.isLoading && uiState.members.isEmpty()) {
            val emptyMessage = uiState.error?.let { FriendlyErrorMapper.map(it).message } ?: "هنوز عضوی اضافه نشده."
            ErrorState(
                message = emptyMessage,
                actionLabel = "افزودن عضو",
                onRetry = { showAddDialog = true },
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(uiState.members, key = { it.id }) { member ->
                MemberCard(
                    member = member,
                    onManage = {
                        editUserId = member.userId
                        editRole = member.role
                        editCanRead = member.canReadHistory
                        editIsActive = member.isActive
                        showEditDialog = true
                    },
                    onRemove = { viewModel.removeMember(groupId, member.userId) },
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("افزودن عضو") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = userId,
                        onValueChange = { userId = it },
                        label = { Text("شناسه کاربر") },
                    )
                    OutlinedTextField(
                        value = TextFieldValue(role),
                        onValueChange = { role = it.text },
                        label = { Text("نقش") },
                        supportingText = { Text("owner / admin / view_all / member") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addMember(groupId, userId.text, role)
                    userId = TextFieldValue("")
                    role = "member"
                    showAddDialog = false
                }) { Text("افزودن") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("انصراف") }
            },
        )
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("مدیریت عضو") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = TextFieldValue(editRole),
                        onValueChange = { editRole = it.text },
                        label = { Text("نقش") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Checkbox(checked = editCanRead, onCheckedChange = { editCanRead = it })
                        Text("دسترسی تاریخچه")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Checkbox(checked = editIsActive, onCheckedChange = { editIsActive = it })
                        Text("فعال")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateMember(groupId, editUserId, editRole, editCanRead, editIsActive)
                    showEditDialog = false
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("انصراف") }
            },
        )
    }
}

@Composable
private fun MemberCard(
    member: GroupMember,
    onManage: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = ShapeLg,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(member.phone, style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AssistChip(onClick = {}, label = { Text("نقش: ${member.role}") })
                if (!member.isActive) {
                    AssistChip(onClick = {}, label = { Text("غیرفعال") })
                }
                if (!member.canReadHistory) {
                    AssistChip(onClick = {}, label = { Text("بدون تاریخچه") })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(onClick = onManage) {
                    Icon(Icons.Outlined.ManageAccounts, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("مدیریت")
                }
                OutlinedButton(onClick = onRemove, colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                )) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("حذف")
                }
            }
        }
    }
}
