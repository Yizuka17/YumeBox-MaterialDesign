package dev.yume.build

import java.io.File

/** Offline Markdown and images; the native reader revalidates the live branch on entry. */
internal fun bundledReadme(root: File): Map<String, ByteArray> {
    val assets = linkedMapOf<String, ByteArray>()
    listOf("README.md", "docs/README.md", "docs/README_ZH_HANS.md", "docs/logo.webp").forEach { path ->
        assets["assets/readme/" + path] = root.resolve(path).readBytes()
    }
    root.resolve("docs/images").walkTopDown().filter(File::isFile).forEach { file ->
        assets["assets/readme/docs/images/" + file.relativeTo(root.resolve("docs/images")).invariantSeparatorsPath] = file.readBytes()
    }
    return assets
}
