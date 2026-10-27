package com.github.yumelira.yumebox.update

/** Numeric versions and SemVer prereleases; build metadata never makes an update newer. */
data class ReleaseVersion(val numbers: List<Int>, val prerelease: List<String>) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        repeat(maxOf(numbers.size, other.numbers.size)) { index ->
            val result = numbers.getOrElse(index) { 0 }.compareTo(other.numbers.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        if (prerelease.isEmpty() != other.prerelease.isEmpty()) return if (prerelease.isEmpty()) 1 else -1
        repeat(maxOf(prerelease.size, other.prerelease.size)) { index ->
            if (index >= prerelease.size) return -1
            if (index >= other.prerelease.size) return 1
            val left = prerelease[index]; val right = other.prerelease[index]
            val a = left.toIntOrNull(); val b = right.toIntOrNull()
            val result = when {
                a != null && b != null -> a.compareTo(b)
                a != null -> -1
                b != null -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return 0
    }

    companion object {
        fun parse(tag: String): ReleaseVersion? {
            val match = Regex("^[vV]?(\\d+(?:\\.\\d+){1,3})(?:[-(]([^+)]*)\\)?)?(?:\\+.*)?$").matchEntire(tag.trim()) ?: return null
            val numbers = match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
            val suffix = match.groupValues[2].lowercase().replace("preview", "alpha")
            val prerelease = Regex("[a-z]+|[0-9]+").findAll(suffix).map { it.value }.toList()
            return ReleaseVersion(numbers, prerelease)
        }
    }
}
