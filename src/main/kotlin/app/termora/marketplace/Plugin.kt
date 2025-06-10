package app.termora.marketplace

import java.io.File

open class Plugin(
    val id: String,
    val name: String,
    val paid: Boolean,
    val versions: MutableList<PluginVersion>,
    val vendor: PluginVendor
)

open class PluginVersion(
    val version: String,
    val since: String,
    val until: String,
    var icon: String,
    var darkIcon: String,
    val descriptions: MutableList<PluginDescription>,
    val downloadUrl: String,
)

class LocalPluginVersion(
    version: String,
    since: String,
    until: String,
    icon: String,
    darkIcon: String,
    descriptions: MutableList<PluginDescription>,
    downloadUrl: String,
    val file: File
) : PluginVersion(version, since, until, icon, darkIcon, descriptions, downloadUrl)

data class PluginDescription(
    val language: String,
    val text: String,
)

data class PluginVendor(
    val name: String,
    val url: String
)