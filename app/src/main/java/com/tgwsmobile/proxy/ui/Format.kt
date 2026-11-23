package com.tgwsmobile.proxy.ui

/** HH:MM:SS for uptime; hours keep counting past 24. */
fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

/** Human-readable byte count with one decimal above 1 MB. */
fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1L shl 20 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1L shl 30 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.2f GB".format(bytes / 1_073_741_824.0)
}
