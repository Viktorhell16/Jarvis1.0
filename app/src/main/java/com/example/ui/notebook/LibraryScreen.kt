package com.example.ui.notebook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.WebAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ArtifactEntity
import com.example.ui.i18n.UiStrings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    artifacts: List<ArtifactEntity>,
    strings: UiStrings,
    onOpenDrawer: () -> Unit,
    onCreateArtifact: (title: String, type: String, category: String, content: String, tagsCsv: String) -> Unit,
    onUpdateArtifact: (ArtifactEntity) -> Unit,
    onTogglePinArtifact: (ArtifactEntity) -> Unit,
    onDeleteArtifact: (Long) -> Unit,
    onRestoreArtifact: (ArtifactEntity) -> Unit,
    onInsertIntoChat: (ArtifactEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf("ALL") }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var isGridView by remember { mutableStateOf(true) }
    var selectedTypeFilter by remember { mutableStateOf("All types") }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var editingArtifact by remember { mutableStateOf<ArtifactEntity?>(null) }
    var contextMenuArtifact by remember { mutableStateOf<ArtifactEntity?>(null) }
    var createTypeToPrompt by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val allTags = remember(artifacts) {
        artifacts.flatMap { it.tagsCsv.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    val filtered = remember(artifacts, selectedTab, selectedTypeFilter, selectedTag) {
        artifacts.filter { item ->
            val tabMatches = when (selectedTab) {
                "FILES" -> item.type in listOf("FILE", "DOCUMENT", "PRESENTATION", "BOOK")
                "IMAGES" -> item.type == "IMAGE"
                "PAGES" -> item.type == "PAGE"
                else -> true
            }
            val typeMatches = when (selectedTypeFilter) {
                "All types" -> true
                "Изображения" -> item.type == "IMAGE"
                "Infographics" -> item.category == "Infographics"
                "Stories" -> item.category == "Stories"
                "Posters" -> item.category == "Posters"
                else -> true
            }
            val tagMatches = selectedTag == null || item.tagsCsv.split(",").map { it.trim() }.contains(selectedTag)
            tabMatches && typeMatches && tagMatches
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(strings.libraryTitle, style = MaterialTheme.typography.titleLarge)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("library_drawer_button")) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCreateSheet = true },
                        modifier = Modifier.testTag("library_create_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = strings.createTitle)
                    }
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.testTag("library_filter_button")
                    ) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filter")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal FilterChip row + optional tag folders
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to strings.filterAll,
                    "FILES" to strings.filterFiles,
                    "IMAGES" to strings.filterImages,
                    "PAGES" to strings.filterPages
                ).forEach { (key, label) ->
                    val isSelected = selectedTab == key && selectedTag == null
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedTab = key; selectedTag = null },
                        label = { Text(label) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.surface
                        ),
                        border = null,
                        modifier = Modifier.testTag("library_chip_$key")
                    )
                }

                allTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { selectedTag = if (selectedTag == tag) null else tag },
                        label = { Text("#$tag") },
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp, start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (selectedTab == "IMAGES") strings.emptyImagesTitle else strings.emptyLibrarySub,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { artifact ->
                        ArtifactGridCard(
                            artifact = artifact,
                            onClick = { editingArtifact = artifact },
                            onLongClick = { contextMenuArtifact = artifact }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { artifact ->
                        ArtifactListRow(
                            artifact = artifact,
                            onClick = { editingArtifact = artifact },
                            onLongClick = { contextMenuArtifact = artifact }
                        )
                    }
                }
            }
        }
    }

    // Long-Press Samsung Notes-style Context Menu Bottom Sheet (4 Actions)
    contextMenuArtifact?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { contextMenuArtifact = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 1. Вставить в чат
                CreateSheetItem(Icons.Outlined.Send, "Вставить в чат", "context_insert_chat") {
                    contextMenuArtifact = null
                    onInsertIntoChat(target)
                }
                // 2. Закрепить / Открепить
                CreateSheetItem(
                    icon = if (target.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    label = if (target.isPinned) "Открепить" else "Закрепить",
                    testTag = "context_pin_note"
                ) {
                    contextMenuArtifact = null
                    onTogglePinArtifact(target)
                }
                // 3. Изменить
                CreateSheetItem(Icons.Outlined.Edit, "Изменить", "context_edit_note") {
                    contextMenuArtifact = null
                    editingArtifact = target
                }
                // 4. Удалить (with Undo Snackbar)
                CreateSheetItem(Icons.Outlined.DeleteOutline, "Удалить", "context_delete_note") {
                    val deletedCopy = target
                    contextMenuArtifact = null
                    onDeleteArtifact(deletedCopy.id)
                    scope.launch {
                        val res = snackbarHostState.showSnackbar(
                            message = "Заметка «${deletedCopy.title}» удалена",
                            actionLabel = "Отменить",
                            duration = SnackbarDuration.Short
                        )
                        if (res == SnackbarResult.ActionPerformed) {
                            onRestoreArtifact(deletedCopy)
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(strings.viewLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { isGridView = false }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            RadioButton(selected = !isGridView, onClick = { isGridView = false })
                            Text(strings.viewList, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { isGridView = true }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            RadioButton(selected = isGridView, onClick = { isGridView = true })
                            Text(strings.viewGrid, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Text(strings.typeLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        listOf("All types", "Изображения", "Infographics", "Stories", "Posters").forEachIndexed { idx, typeOpt ->
                            if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTypeFilter = typeOpt }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(typeOpt, style = MaterialTheme.typography.bodyLarge)
                                RadioButton(selected = selectedTypeFilter == typeOpt, onClick = { selectedTypeFilter = typeOpt })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = strings.createTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
                CreateSheetItem(Icons.Outlined.Description, strings.createDoc) {
                    showCreateSheet = false; createTypeToPrompt = "DOCUMENT"
                }
                CreateSheetItem(Icons.Outlined.Slideshow, strings.createPresentation) {
                    showCreateSheet = false; createTypeToPrompt = "PRESENTATION"
                }
                CreateSheetItem(Icons.Outlined.MenuBook, strings.createBook) {
                    showCreateSheet = false; createTypeToPrompt = "BOOK"
                }
                CreateSheetItem(Icons.Outlined.Image, strings.createImage) {
                    showCreateSheet = false; createTypeToPrompt = "IMAGE"
                }
                CreateSheetItem(Icons.Outlined.WebAsset, strings.createPage) {
                    showCreateSheet = false; createTypeToPrompt = "PAGE"
                }
            }
        }
    }

    createTypeToPrompt?.let { typeKey ->
        SamsungNotesEditorDialog(
            initialTitle = "",
            initialTags = "",
            initialContent = "",
            typeLabel = typeKey,
            onDismiss = { createTypeToPrompt = null },
            onSave = { title, tags, content ->
                val category = if (typeKey == "IMAGE") "Images" else "All"
                onCreateArtifact(title, typeKey, category, content, tags)
                createTypeToPrompt = null
            }
        )
    }

    editingArtifact?.let { artifact ->
        SamsungNotesEditorDialog(
            initialTitle = artifact.title,
            initialTags = artifact.tagsCsv,
            initialContent = artifact.contentOrUri,
            typeLabel = artifact.type,
            onDismiss = { editingArtifact = null },
            onDelete = {
                val deleted = artifact
                editingArtifact = null
                onDeleteArtifact(deleted.id)
                scope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "Удалено: ${deleted.title}",
                        actionLabel = "Отменить"
                    )
                    if (res == SnackbarResult.ActionPerformed) onRestoreArtifact(deleted)
                }
            },
            onSave = { title, tags, content ->
                onUpdateArtifact(
                    artifact.copy(
                        title = title.ifBlank { artifact.title },
                        tagsCsv = tags,
                        contentOrUri = content,
                        previewSummary = content.replace("\n", " ").take(110)
                    )
                )
                editingArtifact = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtifactGridCard(
    artifact: ArtifactEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = if (artifact.isPinned) 1.5.dp else 1.dp,
            color = if (artifact.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("artifact_card_${artifact.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2B2A29),
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconForArtifactType(artifact.type),
                            contentDescription = artifact.type,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = artifact.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (artifact.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            if (artifact.tagsCsv.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = artifact.tagsCsv.split(",").filter { it.isNotBlank() }.joinToString(" ") { "#${it.trim()}" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.weight(1f))
            if (artifact.previewSummary.isNotBlank()) {
                Text(
                    text = artifact.previewSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtifactListRow(
    artifact: ArtifactEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (artifact.isPinned) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconForArtifactType(artifact.type),
                contentDescription = artifact.type,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(artifact.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artifact.previewSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (artifact.isPinned) {
                Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun iconForArtifactType(type: String): ImageVector = when (type) {
    "IMAGE" -> Icons.Outlined.Image
    "PRESENTATION" -> Icons.Outlined.Slideshow
    "BOOK" -> Icons.Outlined.AutoStories
    "PAGE" -> Icons.Outlined.WebAsset
    else -> Icons.Outlined.Description
}

@Composable
private fun CreateSheetItem(
    icon: ImageVector,
    label: String,
    testTag: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .let { if (testTag.isNotEmpty()) it.testTag(testTag) else it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SamsungNotesEditorDialog(
    initialTitle: String,
    initialTags: String,
    initialContent: String,
    typeLabel: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (title: String, tagsCsv: String, content: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var tags by remember { mutableStateOf(initialTags) }
    var content by remember { mutableStateOf(initialContent) }

    val checklistIndices = remember(content) {
        content.lines().mapIndexedNotNull { idx, line ->
            val t = line.trimStart()
            if (t.startsWith("- [ ] ") || t.startsWith("- [x] ") || t.startsWith("- [X] ")) {
                Triple(idx, t.startsWith("- [x] ") || t.startsWith("- [X] "), t.drop(6))
            } else null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notebook • $typeLabel", style = MaterialTheme.typography.titleMedium)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Заголовок") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Теги / папки (через запятую)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Samsung Notes Rich Formatting Toolbar
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { content = "$content\n# " }) {
                            Icon(Icons.Outlined.Title, contentDescription = "Heading")
                        }
                        IconButton(onClick = { content = "$content**жирный текст**" }) {
                            Icon(Icons.Outlined.FormatBold, contentDescription = "Bold")
                        }
                        IconButton(onClick = { content = "$content\n- [ ] Задача" }) {
                            Icon(Icons.Outlined.CheckBox, contentDescription = "Checklist")
                        }
                        IconButton(onClick = { content = "$content\n```\nкод\n```" }) {
                            Icon(Icons.Outlined.Code, contentDescription = "Code block")
                        }
                    }
                }

                // Interactive Checklist Toggle Preview
                if (checklistIndices.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Чек-лист", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            checklistIndices.forEach { (lineIdx, checked, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val lines = content.lines().toMutableList()
                                            lines[lineIdx] = if (checked) "- [ ] $label" else "- [x] $label"
                                            content = lines.joinToString("\n")
                                        }
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { next ->
                                            val lines = content.lines().toMutableList()
                                            lines[lineIdx] = if (next) "- [x] $label" else "- [ ] $label"
                                            content = lines.joinToString("\n")
                                        }
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Текст заметки / Markdown") },
                    minLines = 6,
                    maxLines = 12,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, tags, content) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}
