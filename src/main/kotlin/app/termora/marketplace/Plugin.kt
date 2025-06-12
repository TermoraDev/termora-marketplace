package app.termora.marketplace

import java.io.File

open class Plugin(
    val id: String,
    val name: String,
    val paid: Boolean,
    val icon: String,
    val darkIcon: String,
    val versions: MutableList<PluginVersion>,
    val descriptions: MutableList<PluginDescription>,
    val vendor: PluginVendor
)

open class PluginVersion(
    val version: String,
    val since: String,
    val until: String,
    val downloadUrl: String,
)

class LocalPluginVersion(
    version: String,
    since: String,
    until: String,
    downloadUrl: String,
    val file: File
) : PluginVersion(version, since, until, downloadUrl)

data class PluginDescription(
    val language: String,
    val text: String,
)

data class PluginVendor(
    val name: String,
    val url: String
)