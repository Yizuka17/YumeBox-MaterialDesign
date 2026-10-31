package dev.yume.build

import java.io.File
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/** The installed app documents its own version, including images, without needing GitHub to load. */
internal fun bundledReadme(root: File): Map<String, ByteArray> {
    val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(), AutolinkExtension.create())
    val parser = Parser.builder().extensions(extensions).build()
    val renderer = HtmlRenderer.builder().extensions(extensions).build()
    val assets = linkedMapOf<String, ByteArray>()
    listOf("README.md", "docs/README.md", "docs/README_ZH_HANS.md").forEach { path ->
        val source = root.resolve(path).readText()
        val body = renderer.render(parser.parse(source))
        val html = """<!doctype html><html lang="${if (path == "docs/README.md") "en" else "zh-CN"}">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Readme · YumeBox MD3</title><style>
            :root{/*APP_THEME*/}html{background:var(--bg);color:var(--fg);color-scheme:var(--scheme)}
            body{font:16px/1.65 system-ui,sans-serif;margin:0 auto;padding:20px;max-width:1050px;overflow-wrap:anywhere}
            h1,h2,h3{line-height:1.3;margin-top:1.5em}a{color:var(--link)}img{max-width:100%;height:auto}
            pre,code{background:var(--muted);border-radius:6px}code{padding:.12em .3em}pre{padding:16px;overflow:auto}
            pre code{padding:0}table{display:block;overflow:auto;border-collapse:collapse}td,th{padding:8px;border:1px solid var(--border)}
            blockquote{margin:16px 0;padding:0 16px;border-left:4px solid var(--border)}li{margin:5px 0}
            </style></head><body>$body</body></html>""".trimIndent()
        assets["assets/readme/$path"] = html.toByteArray(Charsets.UTF_8)
    }
    assets["assets/readme/docs/logo.webp"] = root.resolve("docs/logo.webp").readBytes()
    root.resolve("docs/images").walkTopDown().filter(File::isFile).forEach { file ->
        assets["assets/readme/docs/images/${file.relativeTo(root.resolve("docs/images")).invariantSeparatorsPath}"] = file.readBytes()
    }
    return assets
}
