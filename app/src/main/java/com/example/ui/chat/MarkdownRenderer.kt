package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily

private sealed class MdBlock {
    data class TextBlock(val text: String) : MdBlock()
    data class CodeBlock(val language: String, val code: String) : MdBlock()
}

@Composable
fun MarkdownMessageText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { splitMarkdownBlocks(markdown) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.CodeBlock -> CodeSnippetCard(language = block.language, code = block.code)
                is MdBlock.TextBlock -> {
                    val annotated = remember(block.text) { parseInlineMarkdown(block.text) }
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeSnippetCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun splitMarkdownBlocks(input: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = input.lines()
    val textBuf = StringBuilder()
    val codeBuf = StringBuilder()
    var inCode = false
    var codeLang = ""

    for (line in lines) {
        if (line.trimStart().startsWith("```")) {
            if (!inCode) {
                if (textBuf.isNotBlank()) {
                    out.add(MdBlock.TextBlock(textBuf.toString().trimEnd()))
                    textBuf.clear()
                }
                inCode = true
                codeLang = line.trimStart().removePrefix("```").trim()
            } else {
                out.add(MdBlock.CodeBlock(codeLang, codeBuf.toString().trimEnd()))
                codeBuf.clear()
                inCode = false
                codeLang = ""
            }
        } else if (inCode) {
            codeBuf.appendLine(line)
        } else {
            textBuf.appendLine(line)
        }
    }

    if (inCode && codeBuf.isNotEmpty()) {
        out.add(MdBlock.CodeBlock(codeLang, codeBuf.toString().trimEnd()))
    } else if (textBuf.isNotBlank()) {
        out.add(MdBlock.TextBlock(textBuf.toString().trimEnd()))
    }
    if (out.isEmpty()) out.add(MdBlock.TextBlock(input))
    return out
}

private fun parseInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.lines()
    lines.forEachIndexed { idx, rawLine ->
        var line = rawLine
        val isHeader = line.startsWith("### ") || line.startsWith("## ") || line.startsWith("# ")
        if (isHeader) {
            line = line.replace(Regex("^#{1,6}\\s+"), "")
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)) {
                appendStyledSpans(line)
            }
        } else {
            if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
                line = "• " + line.trimStart().drop(2)
            }
            appendStyledSpans(line)
        }
        if (idx < lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.appendStyledSpans(line: String) {
    var i = 0
    while (i < line.length) {
        when {
            line.startsWith("**", i) -> {
                val end = line.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(line.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(line[i++])
                }
            }
            line.startsWith("`", i) -> {
                val end = line.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)) {
                        append(line.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(line[i++])
                }
            }
            line.startsWith("*", i) && !line.startsWith("**", i) -> {
                val end = line.indexOf('*', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(line.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(line[i++])
                }
            }
            else -> append(line[i++])
        }
    }
}
