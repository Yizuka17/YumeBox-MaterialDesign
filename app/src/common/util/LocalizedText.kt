package com.github.yumelira.yumebox.common.util

/** English fallback for fork-specific copy while retaining the selected application locale. */
internal fun localText(zh: String, en: String): String =
    if (java.util.Locale.getDefault().language == "zh") zh else en
