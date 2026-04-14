package ir.siaheh.ui.space

import android.Manifest
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import ir.siaheh.data.model.SpaceMember
import ir.siaheh.data.model.PublicUser
import ir.siaheh.ui.common.FriendlyErrorMapper
import ir.siaheh.ui.components.ErrorState
import ir.siaheh.ui.components.LoadingIndicator
import ir.siaheh.ui.theme.ShapeLg
import ir.siaheh.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceMembersScreen(
    spaceId: String,
    onBack: () -> Unit,
    viewModel: SpaceMembersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf(TextFieldValue("")) }
    var role by remember { mutableStateOf("member") }
    var showEditDialog by remember { mutableStateOf(false) }
    var editUserId by remember { mutableStateOf("") }
    var editRole by remember { mutableStateOf("member") }
    var editCanRead by remember { mutableStateOf(true) }
    var editIsActive by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val phones = readContactPhones(context)
            viewModel.discoverFromContacts(phones)
        }
    }

    LaunchedEffect(spaceId) { viewModel.loadMembers(spaceId) }
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
                actions = {
                    IconButton(onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                        Icon(Icons.Outlined.PersonSearch, contentDescription = "یافتن از مخاطبین")
                    }
                },
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
            if (uiState.suggestions.isNotEmpty()) {
                item {
                    Text(
                        "پیشنهاد برای افزودن",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                }
                items(uiState.suggestions, key = { it.id }) { user ->
                    SuggestionCard(user = user, onAdd = { viewModel.addMember(spaceId, user.username, "member") })
                }
            }
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
                    onRemove = { viewModel.removeMember(spaceId, member.userId) },
                )
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("افزودن عضو با نام کاربری") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            viewModel.searchUsers(it.text)
                        },
                        label = { Text("نام کاربری (بدون @)") },
                    )
                    RoleSelector(currentRole = role, onSelect = { role = it })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addMember(spaceId, username.text, role)
                    username = TextFieldValue("")
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
                    RoleSelector(currentRole = editRole, onSelect = { editRole = it })
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
                    viewModel.updateMember(spaceId, editUserId, editRole, editCanRead, editIsActive)
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
private fun RoleSelector(currentRole: String, onSelect: (String) -> Unit) {
    val roles = listOf("member", "admin", "view_all")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("نقش", style = MaterialTheme.typography.labelLarge)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(roles, key = { it }) { role ->
                AssistChip(
                    onClick = { onSelect(role) },
                    label = { Text(roleLabel(role)) },
                    leadingIcon = if (role == currentRole) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "admin" -> "مدیر"
    "view_all" -> "نمایش همه"
    else -> "عضو"
}

@Composable
private fun SuggestionCard(user: PublicUser, onAdd: () -> Unit) {
    Surface(
        tonalElevation = 1.dp,
        shape = ShapeLg,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = "avatar",
                    modifier = Modifier.size(32.dp),
                )
                Column {
                    Text(if (user.displayName.isNotBlank()) user.displayName else "@${user.username}")
                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onAdd) { Text("افزودن") }
        }
    }
}

@Composable
private fun MemberCard(
    member: SpaceMember,
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AsyncImage(
                    model = member.user.avatarUrl,
                    contentDescription = "avatar",
                    modifier = Modifier.size(32.dp),
                )
                Column {
                    Text(
                        if (member.user.displayName.isNotBlank()) member.user.displayName else "@${member.user.username}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text("@${member.user.username}", style = MaterialTheme.typography.bodySmall)
                }
            }
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
                OutlinedButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("حذف")
                }
            }
        }
    }
}

private fun readContactPhones(context: android.content.Context): List<String> {
    val phones = mutableSetOf<String>()
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        null,
        null,
        null,
    )
    cursor?.use {
        while (it.moveToNext()) {
            val raw = it.getString(0) ?: continue
            val digits = raw.filter { ch -> ch.isDigit() }
            val normalized = when {
                digits.startsWith("98") && digits.length == 12 -> "0${digits.substring(2)}"
                digits.startsWith("9") && digits.length == 10 -> "0$digits"
                else -> digits
            }
            if (normalized.length == 11 && normalized.startsWith("09")) {
                phones.add(normalized)
            }
        }
    }
    return phones.toList()
}
