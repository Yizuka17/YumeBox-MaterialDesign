package com.github.yumelira.yumebox.screen.about

import android.content.Context
import android.graphics.Picture
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.github.yumelira.yumebox.presentation.icon.Yume
import com.github.yumelira.yumebox.presentation.icon.yume.Github
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private fun svgDocument(source: String): org.w3c.dom.Document {
    // Android's DOM implementations differ in supported feature flags; reject declarations
    // outright and install an empty resolver instead of allowing external XML resources.
    require(!source.contains("<!DOCTYPE", ignoreCase = true) && !source.contains("<!ENTITY", ignoreCase = true))
    return DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
    }.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> InputSource(StringReader("")) }
    }.parse(InputSource(StringReader(source)))
}

/** AndroidSVG 1.4 cannot draw SVG2 <image href="data:image/svg+xml;..."> logos.
 * Inline each embedded SVG as a nested vector viewport; do not rasterize it. */
internal fun readmeBadgePicture(source: String): Picture {
    val document = svgDocument(source)
    require(document.documentElement.localName == "svg")
    val images = document.getElementsByTagNameNS("http://www.w3.org/2000/svg", "image")
    (0 until images.length).map { images.item(it) as Element }.forEach { image ->
        val href = image.getAttribute("href").ifBlank {
            image.getAttributeNS("http://www.w3.org/1999/xlink", "href")
        }
        if (href.startsWith("data:image/svg+xml", ignoreCase = true)) {
            val metadata = href.substringBefore(',')
            val payload = href.substringAfter(',', "")
            val bytes = if (metadata.contains(";base64", ignoreCase = true)) Base64.decode(payload, Base64.DEFAULT)
                else URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
            require(bytes.size <= 64 * 1024)
            val logo = svgDocument(bytes.toString(Charsets.UTF_8)).documentElement
            val nested = document.importNode(logo, true) as Element
            listOf("x", "y", "width", "height", "preserveAspectRatio").forEach { name ->
                if (image.hasAttribute(name)) nested.setAttribute(name, image.getAttribute(name))
            }
            image.parentNode.replaceChild(nested, image)
        }
    }
    val writer = StringWriter()
    TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(writer))
    val svg = SVG.getFromString(writer.toString())
    require(svg.documentWidth in 1f..4096f && svg.documentHeight in 1f..4096f)
    return svg.renderToPicture()
}

private fun badgeCache(context: Context, url: String): File {
    val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    return File(context.cacheDir, "readme-badges/" + key + ".svg")
}

private suspend fun fetchBadge(context: Context, url: String): Picture = withContext(Dispatchers.IO) {
    require(url.startsWith("https://img.shields.io/"))
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 8_000
    connection.readTimeout = 10_000
    try {
        require(connection.responseCode == 200)
        require(connection.contentLengthLong <= 256 * 1024)
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 256 * 1024)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val text = bytes.toString(Charsets.UTF_8)
        val picture = readmeBadgePicture(text)
        badgeCache(context, url).apply { parentFile?.mkdirs() }.writeText(text)
        picture
    } finally { connection.disconnect() }
}

@Composable
internal fun NativeReadmeBadge(url: String, description: String, onClick: () -> Unit) {
    val context = LocalContext.current
    val picture by produceState<Picture?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching { readmeBadgePicture(badgeCache(context, url).readText()) }.getOrNull()
        }
        try { value = fetchBadge(context, url) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { android.util.Log.w("ReadmeBadge", "Unable to refresh badge", error) }
    }
    val image = picture
    if (image != null && image.width > 0 && image.height > 0) {
        Canvas(Modifier.height(28.dp).aspectRatio(image.width.toFloat() / image.height)
            .semantics { contentDescription = description }.clickable(onClick = onClick)) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.scale(size.width / image.width, size.height / image.height)
                native.drawPicture(image)
                native.restore()
            }
        }
    } else Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickable(onClick = onClick).semantics { contentDescription = description }) {
        Row(Modifier.heightIn(min = 28.dp).padding(horizontal = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(when {
                "/license/" in url -> Icons.Rounded.Description
                "/downloads/" in url -> Icons.Rounded.Download
                else -> Yume.Github
            }, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(description, style = MaterialTheme.typography.labelMedium)
        }
    }
}
