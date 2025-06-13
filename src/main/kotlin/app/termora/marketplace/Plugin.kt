package app.termora.marketplace

import java.io.File

open class Plugin(
    val id: String,
    val name: String,
    val paid: Boolean,
    var icon: String,
    var darkIcon: String,
    val versions: MutableList<PluginVersion>,
    var descriptions: MutableList<PluginDescription>,
    val vendor: PluginVendor
)

open class PluginVersion(
    val version: String,
    val since: String,
    val until: String,
    val downloadUrl: String,
    val signature: String,
    val size: Long,
)

class LocalPluginVersion(
    version: String,
    since: String,
    until: String,
    downloadUrl: String,
    signature: String,
    size: Long,
    val file: File,
) : PluginVersion(version, since, until, downloadUrl, signature, size)

data class PluginDescription(
    val language: String,
    val text: String,
)

data class PluginVendor(
    val name: String,
    val url: String
)