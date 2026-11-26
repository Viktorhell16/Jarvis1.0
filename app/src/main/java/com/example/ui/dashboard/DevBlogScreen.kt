package com.example.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.DevPostEntity
import com.example.notifications.JarvisNotificationHelper
import com.example.ui.i18n.UiStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevBlogScreen(
    posts: List<DevPostEntity>,
    strings: UiStrings,
    onBack: () -> Unit,
    onAddPost: (title: String, body: String, category: String, versionTag: String) -> Unit,
    onUpdatePost: (DevPostEntity) -> Unit,
    onDeletePost: (Long) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val categories = listOf("Все", "Добавлено", "Изменено", "Исправлено")
    var selectedCategory by remember { mutableStateOf("Все") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingPost by remember { mutableStateOf<DevPostEntity?>(null) }

    val filtered = remember(posts, selectedCategory) {
        if (selectedCategory == "Все") posts else posts.filter { it.category == selectedCategory }
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.newsAndUpdates) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("devblog_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("devblog_add_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add post")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, maxLines = 1, softWrap = false) }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { post ->
                    val badgeColor = when (post.category) {
                        "Добавлено" -> Color(0xFF22C55E)
                        "Исправлено" -> Color(0xFFF59E0B)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingPost = post }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = badgeColor.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = post.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeColor,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = post.versionTag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = dateFormat.format(Date(post.createdAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(onClick = { editingPost = post }) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Edit")
                                    }
                                    IconButton(onClick = { onDeletePost(post.id) }) {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete")
                                    }
                                }
                            }
                            Text(
                                text = post.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = post.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        DevPostEditorDialog(
            initialTitle = "",
            initialBody = "",
            initialCategory = "Добавлено",
            initialVersion = "v1.2",
            dialogTitle = "Новая запись DevBlog",
            showNotificationCheckbox = true,
            onDismiss = { showCreateDialog = false },
            onSave = { title, body, category, version, sendNotification ->
                onAddPost(title, body, category, version)
                if (sendNotification) {
                    JarvisNotificationHelper.showUpdateChangelogNotification(
                        context = context,
                        title = title.ifBlank { "Обновление JARVIS" },
                        versionTag = version.ifBlank { "v1.2" },
                        summary = body.ifBlank { title },
                        category = category
                    )
                }
                showCreateDialog = false
            }
        )
    }

    editingPost?.let { target ->
        DevPostEditorDialog(
            initialTitle = target.title,
            initialBody = target.body,
            initialCategory = target.category,
            initialVersion = target.versionTag,
            dialogTitle = "Редактировать запись",
            showNotificationCheckbox = false,
            onDismiss = { editingPost = null },
            onSave = { title, body, category, version, _ ->
                onUpdatePost(
                    target.copy(
                        title = title.ifBlank { target.title },
                        body = body,
                        category = category,
                        versionTag = version.ifBlank { target.versionTag }
                    )
                )
                editingPost = null
            }
        )
    }
}

@Composable
private fun DevPostEditorDialog(
    initialTitle: String,
    initialBody: String,
    initialCategory: String,
    initialVersion: String,
    dialogTitle: String,
    showNotificationCheckbox: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String, category: String, version: String, sendNotification: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var body by remember { mutableStateOf(initialBody) }
    var category by remember { mutableStateOf(initialCategory) }
    var version by remember { mutableStateOf(initialVersion) }
    var sendNotification by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("Добавлено", "Изменено", "Исправлено").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, maxLines = 1, softWrap = false) }
                        )
                    }
                }
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Версия (например v1.2)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Заголовок") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Описание изменений") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showNotificationCheckbox) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sendNotification = !sendNotification }
                            .padding(vertical = 4.dp)
                            .testTag("devblog_send_notification_checkbox")
                    ) {
                        Checkbox(
                            checked = sendNotification,
                            onCheckedChange = { sendNotification = it }
                        )
                        Text(
                            text = "Отправить список обновлений в шторку уведомлений",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, body, category, version, sendNotification) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
