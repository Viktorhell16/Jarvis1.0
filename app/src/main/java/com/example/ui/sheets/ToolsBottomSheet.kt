package com.example.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.i18n.UiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsBottomSheet(
    strings: UiStrings,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenLibrary: () -> Unit,
    onLaunchMaps: () -> Unit,
    onSelectToolPrompt: (prefill: String, enableCanvas: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            // Top Horizontal Carousel of large rounded-square (20.dp) tonal cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickAttachCard(
                    icon = Icons.Outlined.FolderOpen,
                    label = strings.toolFiles,
                    testTag = "tool_files_card",
                    onClick = { onDismiss(); onPickFile() }
                )
                QuickAttachCard(
                    icon = Icons.Outlined.CloudQueue,
                    label = strings.toolDrive,
                    testTag = "tool_drive_card",
                    onClick = { onDismiss(); onOpenSources() }
                )
                QuickAttachCard(
                    icon = Icons.Outlined.Book,
                    label = strings.toolNotebooks,
                    testTag = "tool_notebooks_card",
                    onClick = { onDismiss(); onOpenLibrary() }
                )
                QuickAttachCard(
                    icon = Icons.Outlined.Map,
                    label = strings.toolMap,
                    testTag = "tool_map_card",
                    onClick = { onDismiss(); onLaunchMaps() }
                )
            }

            Spacer(Modifier.height(12.dp))

            // Vertical Tool List
            ToolListRow(
                icon = Icons.Outlined.Image,
                title = strings.toolImagesTitle,
                subtitle = strings.toolImagesSub,
                testTag = "tool_row_images",
                onClick = {
                    onSelectToolPrompt("Создай детальный концепт и промпт изображения: ", false)
                    onDismiss()
                }
            )
            ToolListRow(
                icon = Icons.Outlined.Movie,
                title = strings.toolVideoTitle,
                subtitle = strings.toolVideoSub,
                testTag = "tool_row_video",
                onClick = {
                    onSelectToolPrompt("Напиши раскадровку и сценарий видеоролика: ", false)
                    onDismiss()
                }
            )
            ToolListRow(
                icon = Icons.Outlined.MusicNote,
                title = strings.toolMusicTitle,
                subtitle = strings.toolMusicSub,
                testTag = "tool_row_music",
                onClick = {
                    onSelectToolPrompt("Сочини структуру трека, аккорды и текст песни: ", false)
                    onDismiss()
                }
            )
            ToolListRow(
                icon = Icons.Outlined.DashboardCustomize,
                title = strings.toolCanvasTitle,
                subtitle = strings.toolCanvasSub,
                testTag = "tool_row_canvas",
                onClick = {
                    onSelectToolPrompt("", true)
                    onDismiss()
                }
            )
            ToolListRow(
                icon = Icons.Outlined.TravelExplore,
                title = strings.toolDeepResearchTitle,
                subtitle = strings.toolDeepResearchSub,
                testTag = "tool_row_research",
                onClick = {
                    onSelectToolPrompt("Проведи Deep Research с источниками и выводами по теме: ", false)
                    onDismiss()
                }
            )
            ToolListRow(
                icon = Icons.Outlined.School,
                title = strings.toolStudyTitle,
                subtitle = strings.toolStudySub,
                testTag = "tool_row_study",
                onClick = {
                    onSelectToolPrompt("Объясни пошагово с примерами и проверкой понимания: ", false)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun QuickAttachCard(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(width = 100.dp, height = 88.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ToolListRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
