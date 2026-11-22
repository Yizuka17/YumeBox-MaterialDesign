package com.github.yumelira.yumebox.screen.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.github.panpf.sketch.AsyncImage as SketchAsyncImage
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ImageRequest
import com.github.panpf.sketch.request.LoadState
import com.github.yumelira.yumebox.update.ReleaseCoverPreview
import org.commonmark.node.*
import org.commonmark.node.Text as MarkdownText
import org.commonmark.node.Paragraph as MarkdownParagraph
import org.commonmark.parser.Parser
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.*

internal data class ReadmeBlock(val node: Node, val anchor: String? = null)

internal fun Node.children(): List<Node> = generateSequence(firstChild) { it.next }.toList()
private fun Node.plainText(): String = when (this) {
    is MarkdownText -> literal
    is Code -> literal
    is SoftLineBreak, is HardLineBreak -> " "
    else -> children().joinToString("") { it.plainText() }
}

/** Preserve the README's HTML images as native images, without executing HTML or CSS. */
internal fun parseReadme(markdown: String): List<ReadmeBlock> {
    fun attribute(tag: String, key: String) =
        Regex("""\b""" + key + """\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1).orEmpty()
    val normalized = markdown.removePrefix("\uFEFF").replace(Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE)) { match ->
        val source = attribute(match.value, "src")
        val alt = attribute(match.value, "alt").replace("]", "")
        if (source.isBlank()) "" else "\n\n![" + alt + "](<" + source + ">)\n\n"
    }.replace(Regex("</?div\\b[^>]*>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "  \n")
    val parser = Parser.builder().extensions(listOf(TablesExtension.create(),
        StrikethroughExtension.create(), AutolinkExtension.create())).build()
    val headings = mutableMapOf<String, Int>()
    return parser.parse(normalized).children().map { node ->
        val anchor = if (node is Heading) {
            val slug = node.plainText().lowercase().replace(Regex("[^\\p{L}\\p{N}_ -]"), "")
                .trim().replace(' ', '-')
            val count = headings[slug] ?: 0
            headings[slug] = count + 1
            slug + if (count == 0) "" else "-" + count
        } else null
        ReadmeBlock(node, anchor)
    }
}

private fun Node.hasText(): Boolean = when (this) {
    is Image -> false
    is MarkdownText -> literal.isNotBlank()
    is Code -> literal.isNotBlank()
    else -> children().any { it.hasText() }
}

private data class ReadmeImage(val image: Image, val link: String?)
private fun images(node: Node, link: String? = null): List<ReadmeImage> = when (node) {
    is Image -> listOf(ReadmeImage(node, link))
    is Link -> node.children().flatMap { images(it, node.destination) }
    else -> node.children().flatMap { images(it, link) }
}

@Composable
internal fun NativeReadmeBlock(node: Node, path: String, openLink: (String) -> Unit) {
    when (node) {
        is Heading -> ReadmeText(node, openLink, when (node.level) {
            1 -> MaterialTheme.typography.headlineLarge
            2 -> MaterialTheme.typography.headlineMedium
            3 -> MaterialTheme.typography.titleLarge
            else -> MaterialTheme.typography.titleMedium
        })
        is MarkdownParagraph -> {
            val media = remember(node) { images(node) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (node.hasText()) ReadmeText(node, openLink)
                media.forEach { ReadmePicture(it, path, openLink) }
            }
        }
        is BulletList, is OrderedList -> {
            val start = (node as? OrderedList)?.markerStartNumber ?: 1
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                node.children().forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (node is OrderedList) (start + index).toString() + "." else "–",
                            style = MaterialTheme.typography.bodyLarge)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.children().forEach { NativeReadmeBlock(it, path, openLink) }
                        }
                    }
                }
            }
        }
        is BlockQuote -> Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                node.children().forEach { NativeReadmeBlock(it, path, openLink) }
            }
        }
        is FencedCodeBlock -> ReadmeCode(node.literal)
        is IndentedCodeBlock -> ReadmeCode(node.literal)
        is ThematicBreak -> HorizontalDivider()
        is TableBlock -> {
            val rows = node.children().flatMap { it.children() }
            Column(Modifier.horizontalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp)) {
                rows.forEach { row ->
                    Row {
                        row.children().forEach { cell ->
                            Box(Modifier.width(200.dp).padding(10.dp)) {
                                ReadmeText(cell, openLink, if ((cell as? TableCell)?.isHeader == true)
                                    MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
        is HtmlBlock -> {
            val plain = remember(node) { HtmlCompat.fromHtml(node.literal, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim() }
            if (plain.isNotEmpty()) Text(plain, style = MaterialTheme.typography.bodyLarge)
        }
        else -> node.children().forEach { NativeReadmeBlock(it, path, openLink) }
    }
}

@Composable
private fun ReadmeCode(value: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Text(value.trimEnd(), Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
    }
}

@Composable
private fun ReadmeText(node: Node, openLink: (String) -> Unit,
    style: TextStyle = MaterialTheme.typography.bodyLarge) {
    val primary = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val boldWeight = FontWeight(((style.fontWeight?.weight ?: 400) + 200).coerceAtMost(1000))
    val text = remember(node, primary, codeBackground, boldWeight, openLink) {
        buildAnnotatedString {
            fun appendNode(child: Node) {
                when (child) {
                    is MarkdownText -> append(child.literal)
                    is Image -> Unit
                    is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(child.literal) }
                    is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { child.children().forEach(::appendNode) }
                    is StrongEmphasis -> withStyle(SpanStyle(fontWeight = boldWeight)) { child.children().forEach(::appendNode) }
                    is Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { child.children().forEach(::appendNode) }
                    is Link -> withLink(LinkAnnotation.Clickable(
                        child.destination,
                        styles = TextLinkStyles(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { openLink(child.destination) },
                    )) { child.children().forEach(::appendNode) }
                    is SoftLineBreak -> append(" ")
                    is HardLineBreak -> append("\n")
                    is HtmlInline -> append(HtmlCompat.fromHtml(child.literal, HtmlCompat.FROM_HTML_MODE_COMPACT).toString())
                    else -> child.children().forEach(::appendNode)
                }
            }
            appendNode(node)
        }
    }
    Text(text, style = style, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun ReadmePicture(media: ReadmeImage, path: String, openLink: (String) -> Unit) {
    val context = LocalContext.current
    val remote = remember(path, media.image.destination) {
        val uri = readmeLink(path, media.image.destination)
        if (uri.host == "github.com" && uri.path.startsWith("/Yizuka17/YumeBox-MaterialDesign/blob/Yume/"))
            uri.toString().replace("https://github.com/Yizuka17/YumeBox-MaterialDesign/blob/Yume/",
                "https://raw.githubusercontent.com/Yizuka17/YumeBox-MaterialDesign/Yume/")
        else uri.toString()
    }
    if (!remote.startsWith("https://") && !remote.startsWith("http://")) return
    val badge = remote.startsWith("https://img.shields.io/")
    val raster = if (badge) remote.replace("https://img.shields.io/", "https://raster.shields.io/") else remote
    var source by remember(remote) { mutableStateOf(raster) }
    var preview by remember(remote) { mutableStateOf(false) }
    val state = rememberAsyncImageState()
    val alt = media.image.plainText()
    val logo = remote.substringBefore('?').endsWith("/logo.webp")
    val info = (state.loadState as? LoadState.Success)?.result?.imageInfo
    val ratio = info?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height }
        ?: if (logo) 1f else if (badge) 5f else 16f / 9f
    LaunchedEffect(state.loadState, remote) {
        if (state.loadState is LoadState.Error && badge && source == raster) source = remote
        if (state.loadState is LoadState.Error && source == remote &&
            remote.startsWith("https://raw.githubusercontent.com/Yizuka17/YumeBox-MaterialDesign/Yume/docs/")) {
            val local = remote.substringAfter("/Yume/")
            val exists = runCatching { context.assets.open("readme/" + local).use { } }.isSuccess
            if (exists) source = "file:///android_asset/readme/" + local
        }
    }
    if (state.loadState is LoadState.Error && alt.isNotBlank()) Text(alt, style = MaterialTheme.typography.bodyMedium)
    else SketchAsyncImage(
        request = ImageRequest(context, source),
        contentDescription = alt.ifBlank { "Readme图片" },
        state = state,
        contentScale = ContentScale.Fit,
        modifier = (if (logo) Modifier.width(96.dp) else if (badge) Modifier.height(28.dp)
            else Modifier.fillMaxWidth()).aspectRatio(ratio).let { modifier ->
            modifier.clickable {
                if (badge && media.link != null) openLink(media.link) else preview = true
            }
        },
    )
    if (preview) ReleaseCoverPreview(source, onDismiss = { preview = false },
        description = alt.ifBlank { "Readme图片" })
}
