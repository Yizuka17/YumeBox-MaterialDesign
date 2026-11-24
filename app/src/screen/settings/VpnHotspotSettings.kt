package com.github.yumelira.yumebox.screen.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import org.xmlpull.v1.XmlPullParser

/** Only expose the shortcut when the system actually declares the VPN sharing preference. */
internal object VpnHotspotSettings {
    private const val ACTION = "android.settings.TETHER_SETTINGS"
    private const val PREFERENCE = "tethering_allow_vpn_upstreams"
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    fun resolve(context: Context): Intent? = runCatching {
        val intent = Intent(ACTION)
        val activity = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo ?: return null
        if (!activity.exported || activity.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) return null
        val resources = context.packageManager.getResourcesForApplication(activity.applicationInfo)
        val id = resources.getIdentifier("tether_prefs", "xml", activity.packageName)
        if (id == 0) return null
        var supported = false
        resources.getXml(id).use { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG &&
                    parser.getAttributeValue(ANDROID_NS, "key") == PREFERENCE
                ) {
                    supported = true
                    break
                }
                parser.next()
            }
        }
        if (!supported) return null
        intent.setClassName(activity.packageName, activity.name)
            .putExtra(":settings:fragment_args_key", PREFERENCE)
            .putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(":settings:fragment_args_key", PREFERENCE)
            })
    }.getOrNull()
}
