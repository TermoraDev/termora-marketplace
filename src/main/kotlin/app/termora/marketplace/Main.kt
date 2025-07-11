package app.termora.marketplace

import kotlinx.html.*
import kotlinx.html.dom.create
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.codec.binary.Base64
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.SystemUtils
import org.apache.commons.lang3.time.DateFormatUtils
import org.dom4j.DocumentHelper
import org.dom4j.io.SAXReader
import org.dom4j.io.XMLWriter
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GitHubBuilder
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.util.*
import java.util.jar.JarFile
import java.util.zip.Deflater
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

private val log = LoggerFactory.getLogger("Main")

fun main(args: Array<String>) {

    val config = parseArgs(args)

    // 解析本地插件库
    val locallyPlugins = getLocallyPlugins(config)

    // 获取到最后一个
    val latestPlugins = getLastestPlugins(config)

    // 合并插件
    val plugins = mergePlugins(locallyPlugins, latestPlugins)

    if (plugins.isEmpty()) {
        log.info("No changed")
        return
    }

    // 创建 GitHub release
    val release = createRelease(config)

    // 上传文件
    uploadAssets(plugins, release)

    // plugins.xml
    release.uploadAsset("plugins.xml", pluginsToXml(plugins).toByteArray().inputStream(), "text/xml")
    log.info("plugins.xml upload successfully")

    release.uploadAsset("plugins.html", pluginsToHtml(plugins).toByteArray().inputStream(), "text/html")
    log.info("plugins.html upload successfully")

    // 正式发布
    release.update().draft(false).update()


    log.info("Released: ${release.name}")
}

private fun createRelease(config: MarketplaceConfig): GHRelease {
    return config.repo.createRelease(config.tagName)
        .name(config.releaseName)
        .draft(true)
        .create()
}

private fun uploadAssets(plugins: List<Plugin>, release: GHRelease) {
    for (plugin in plugins) {
        for (version in plugin.versions) {
            if (version is LocalPluginVersion) {
                log.info("Uploading ${version.file.name}")
                release.uploadAsset(version.file, "application/zip")
                log.info("${version.file.name} upload successfully")
            }
        }
    }
}

private fun mergePlugins(locallyPlugins: List<Plugin>, latestPlugins: List<Plugin>): List<Plugin> {
    val plugins = mutableListOf<Plugin>()
    plugins.addAll(latestPlugins)

    var changed = false

    for (plugin in locallyPlugins) {
        val remotePlugin = plugins.firstOrNull { it.id == plugin.id }
        val localVersion = plugin.versions.first()

        if (remotePlugin == null) {
            plugins.add(plugin)
            changed = true
            log.info("Add new plugin {}, version: {}", plugin.id, localVersion.version)
            continue
        }

        // 如果远程已经包含了该版本，那么忽略
        if (remotePlugin.versions.any { it.version == localVersion.version }) {
            log.info("Plugin {}({}) already exists", plugin.id, localVersion.version)
            continue
        }

        // 版本太低
        if (remotePlugin.versions.any { it.version > localVersion.version }) {
            log.warn("Plugin {}({}) version number is too low", plugin.id, localVersion.version)
            continue
        }

        remotePlugin.icon = plugin.icon
        remotePlugin.darkIcon = plugin.darkIcon
        remotePlugin.descriptions = plugin.descriptions

        remotePlugin.versions.addFirst(localVersion)
        changed = true

        log.info("Add plugin {} new version: {}", plugin.id, localVersion.version)
    }

    return if (changed) plugins else emptyList()
}

private fun getLocallyPlugins(config: MarketplaceConfig): List<Plugin> {
    if (config.pluginsDirectory.exists().not() || config.pluginsDirectory.isDirectory.not()) return emptyList()
    val plugins = mutableListOf<Plugin>()

    for (pluginDirectory in config.pluginsDirectory.listFiles { it.isDirectory } ?: emptyArray()) {
        for (jar in pluginDirectory.listFiles { file -> file.name.endsWith(".jar") } ?: emptyArray()) {
            plugins.add(parseJarFile(jar, config) ?: continue)
        }
    }

    return plugins
}

private fun pluginsToXml(plugins: List<Plugin>): String {
    val sw = StringWriter()
    val writer = XMLWriter(sw)

    val document = DocumentHelper.createDocument()
    val root = document.addElement("plugins")
    for (plugin in plugins) {
        val pluginElement = root.addElement("plugin")
        pluginElement.addElement("id").addText(plugin.id)
        pluginElement.addElement("name").addText(plugin.name)
        pluginElement.addElement("icon").addCDATA(plugin.icon)
        pluginElement.addElement("dark-icon").addCDATA(plugin.darkIcon)

        if (plugin.paid) pluginElement.addElement("paid")

        if (plugin.vendor.url.isNotBlank() || plugin.vendor.name.isNotBlank()) {
            val vendorElement = pluginElement.addElement("vendor")
            if (plugin.vendor.name.isNotBlank()) {
                vendorElement.addText(plugin.vendor.name)
            }
            if (plugin.vendor.url.isNotBlank()) {
                vendorElement.addAttribute("url", plugin.vendor.url)
            }
        }

        // versions
        val versionsElement = pluginElement.addElement("versions")
        for (version in plugin.versions) {
            val versionElement = versionsElement.addElement("version")
            versionElement.addElement("version").addText(version.version)
            versionElement.addElement("since").addCDATA(version.since)
            versionElement.addElement("until").addCDATA(version.until)
            versionElement.addElement("download-url").addText(version.downloadUrl)
            versionElement.addElement("size").addText("${version.size}")
            versionElement.addElement("signature").addCDATA(version.signature)
        }

        val descriptionsElement = pluginElement.addElement("descriptions")
        for (description in plugin.descriptions) {
            val descriptionElement = descriptionsElement.addElement("description")
            if (description.language.isNotBlank()) {
                descriptionElement.addAttribute("language", description.language)
            }
            descriptionElement.addCDATA(description.text)
        }
    }

    writer.write(document)

    return sw.toString()
}

private fun pluginsToJson(plugins: List<Plugin>): String {
    val plugins = buildJsonArray {
        for (plugin in plugins) {
            add(buildJsonObject {
                put("id", plugin.id)
                put("name", plugin.name)
                put("icon", plugin.icon)
                put("darkIcon", plugin.darkIcon)
                put("paid", plugin.paid)

                if (plugin.vendor.url.isNotBlank() || plugin.vendor.name.isNotBlank()) {
                    put("vendor", buildJsonObject {
                        if (plugin.vendor.name.isNotBlank()) {
                            put("name", plugin.vendor.name)
                        }
                        if (plugin.vendor.url.isNotBlank()) {
                            put("url", plugin.vendor.url)
                        }
                    })
                }

                put("versions", buildJsonArray {
                    for (version in plugin.versions) {
                        add(buildJsonObject {
                            put("version", version.version)
                            put("since", version.since)
                            put("until", version.until)
                            put("downloadUrl", version.downloadUrl)
                            put("signature", version.signature)
                        })
                    }
                })
                put("descriptions", buildJsonArray {
                    for (description in plugin.descriptions) {
                        add(buildJsonObject {
                            if (description.language.isNotBlank()) {
                                put("language", description.language)
                            }
                            put("text", description.text)
                        })
                    }
                })
            })
        }
    }

    return Json.encodeToString(plugins)
}

private fun pluginsToHtml(plugins: List<Plugin>): String {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
    document.appendChild(document.create.html {
        head {
            meta(
                "viewport",
                "width=device-width, user-scalable=no, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0"
            )
            title("Termora's plugin marketplace")
        }

        body {
            h1 {
                a {
                    target = "_blank"
                    href = "https://github.com/TermoraDev/Termora"
                    text("Termora")
                }
                text("'s plugin marketplace")
            }
            hr()

            for (plugin in plugins) {
                h2 { text(plugin.name) }

                details {
                    open = false
                    for (description in plugin.descriptions) {
                        if (description == plugin.descriptions.first()) {
                            summary {
                                style = "color: dimgray;cursor: pointer;"
                                text(description.text)
                            }
                        } else {
                            p {
                                span {
                                    text(description.language)
                                    text(":")
                                    style = "color: gray"
                                }
                                span {
                                    style = "margin-left: 8px;color: dimgray"
                                    text(description.text)
                                }
                            }
                        }
                    }
                }

                ul {
                    for (version in plugin.versions) {
                        li {
                            a {
                                target = "_blank"
                                href = version.downloadUrl
                                text(version.version)

                                if (version != plugin.versions.first()) {
                                    style = "color: black"
                                }
                            }

                            code {
                                style = "margin-left: 10px; color: gray"
                                span { text(version.since) }
                                span { text(version.until) }
                            }
                        }
                    }
                }

                // margin
                div { style = "margin-bottom: 40px; " }

            }
        }
    })

    val trans = TransformerFactory.newInstance().newTransformer()
    val sw = StringWriter()
    trans.transform(DOMSource(document), StreamResult(sw))
    return sw.toString()
}

private fun parseJarFile(jar: File, config: MarketplaceConfig): Plugin? {
    val jarFile = JarFile(jar)

    val plugin = jarFile.getEntry("META-INF/plugin.xml") ?: return null
    val icon = jarFile.getEntry("META-INF/pluginIcon.svg") ?: return null
    val darkIcon = jarFile.getEntry("META-INF/pluginIcon_dark.svg") ?: icon

    val document = jarFile.getInputStream(plugin).use { SAXReader().read(it) }
    val root = document.rootElement
    if (root.name != "termora-plugin") return null

    // 存在 paid 标签则需要订阅
    val paid = root.element("paid") != null

    val version = root.element("version")?.textTrim ?: return null
    val name = root.element("name")?.textTrim ?: return null
    val id = root.element("id")?.textTrim ?: return null
    val entry = root.element("entry")?.textTrim ?: return null
    val termoraVersion = root.element("termora-version") ?: return null
    val since = termoraVersion.attributeValue("since") ?: return null
    val until = termoraVersion.attributeValue("until") ?: StringUtils.EMPTY


    if (StringUtils.isAnyBlank(version, name, id, entry, since)) return null

    val descriptions = mutableListOf<PluginDescription>()
    for (element in root.element("descriptions").elements("description")) {
        descriptions.add(
            PluginDescription(
                element.attributeValue("language") ?: StringUtils.EMPTY,
                element.textTrim
            )
        )
    }

    val iconSvg = jarFile.getInputStream(icon).use { IOUtils.toString(it, Charsets.UTF_8) }
    val darkIconSvg = jarFile.getInputStream(darkIcon).use { IOUtils.toString(it, Charsets.UTF_8) }

    val vendor = root.element("vendor")

    IOUtils.closeQuietly(jarFile)

    val archiveStreamFactory = ArchiveStreamFactory(Charsets.UTF_8.name())

    val tempFile = FileUtils.getFile(
        SystemUtils.getJavaIoTmpDir(),
        UUID.randomUUID().toString(), "${id}-${version}.zip"
    )
    FileUtils.forceMkdirParent(tempFile)

    tempFile.outputStream().use {
        archiveStreamFactory.createArchiveOutputStream<ZipArchiveOutputStream>(
            ArchiveStreamFactory.ZIP,
            it
        ).use { archive ->
            archive.setLevel(Deflater.DEFAULT_COMPRESSION)
            archive.setFallbackToUTF8(true)
            for (item in jar.parentFile.listFiles { file -> file.isFile } ?: emptyArray()) {
                val entry = ZipArchiveEntry(item.name)
                archive.putArchiveEntry(entry)
                item.inputStream().use { input -> IOUtils.copy(input, archive) }
                archive.closeArchiveEntry()
            }
        }
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        FileUtils.deleteQuietly(tempFile)
    })

    log.info("Loaded {} plugin, version: {}", id, version)

    val privateKey = config.privateKey
    var signature = StringUtils.EMPTY
    if (privateKey != null) {
        signature = Base64.encodeBase64URLSafeString(
            tempFile.inputStream().use { Ed25519.sign(privateKey, it.readAllBytes()) })
    }

    return Plugin(
        id = id,
        name = name,
        icon = iconSvg,
        darkIcon = darkIconSvg,
        paid = paid,
        descriptions = descriptions,
        versions = mutableListOf(
            LocalPluginVersion(
                version = version,
                since = since,
                until = until,
                file = tempFile,
                signature = signature,
                downloadUrl = "https://github.com/${config.repo.fullName}/releases/download/${config.tagName}/${tempFile.name}",
                size = Files.size(tempFile.toPath()),
            )
        ),
        vendor = PluginVendor(
            name = vendor?.textTrim ?: StringUtils.EMPTY,
            url = vendor?.attributeValue("url") ?: StringUtils.EMPTY
        ),
    )

}

private fun getLastestPlugins(config: MarketplaceConfig): List<Plugin> {
    val release = config.repo.latestRelease
    if (release == null) return emptyList()

    for (asset in release.listAssets()) {
        if (asset.name == "plugins.xml") {
            val response = config.okHttpClient
                .newCall(Request.Builder().get().url(asset.url).header("Accept", "application/octet-stream").build())
                .execute()
            if (response.isSuccessful.not()) {
                IOUtils.closeQuietly(response)
                throw IllegalArgumentException("response error: ${response.code}")
            }
            val text = response.use { response.body?.use { it.string() } }
                ?: throw IllegalArgumentException("response is empty")
            return parsePluginsXml(text)
        }
    }

    return emptyList()

}

private fun parsePluginsXml(text: String): List<Plugin> {
    val document = SAXReader().read(InputSource(StringReader(text)))
    val plugins = mutableListOf<Plugin>()
    val root = document.rootElement

    for (element in root.elements("plugin")) {
        val id = element.element("id")?.textTrim ?: continue
        val name = element.element("name")?.textTrim ?: continue
        val paid = element.element("paid") != null
        val vendor = element.element("vendor")
        val icon = element.element("icon")?.textTrim ?: StringUtils.EMPTY
        val darkIcon = element.element("darkIcon")?.textTrim ?: StringUtils.EMPTY
        val versions = mutableListOf<PluginVersion>()
        val descriptions = mutableListOf<PluginDescription>()

        for (desc in element.element("descriptions")?.elements("description") ?: emptyList()) {
            val description = desc.textTrim ?: StringUtils.EMPTY
            val language = StringUtils.defaultString(desc.attributeValue("language"))
            descriptions.add(PluginDescription(language, description))
        }


        for (item in element.element("versions")?.elements("version") ?: emptyList()) {
            val version = item.element("version")?.textTrim ?: continue
            val since = item.element("since")?.textTrim ?: continue
            val until = item.element("until")?.textTrim ?: StringUtils.EMPTY
            val downloadUrl = item.element("download-url")?.textTrim ?: StringUtils.EMPTY
            val signature = item.element("signature")?.textTrim ?: StringUtils.EMPTY
            val size = item.element("size")?.textTrim?.toLongOrNull() ?: 0

            versions.add(
                PluginVersion(
                    version = version,
                    since = since,
                    until = until,
                    signature = signature,
                    downloadUrl = downloadUrl,
                    size = size,
                )
            )
        }

        plugins.add(
            Plugin(
                id = id,
                name = name,
                paid = paid,
                icon = icon,
                darkIcon = darkIcon,
                descriptions = descriptions,
                versions = versions,
                vendor = PluginVendor(
                    name = vendor?.textTrim ?: StringUtils.EMPTY,
                    url = vendor?.attributeValue("url") ?: StringUtils.EMPTY
                )
            )
        )
    }

    return plugins
}

private fun parseArgs(args: Array<String>): MarketplaceConfig {

    val options = Options()

    options.addOption(
        Option.builder("plugins")
            .required()
            .hasArg()
            .desc("Plugins directory")
            .build()
    )

    options.addOption(
        Option.builder("token")
            .required()
            .hasArg()
            .desc("GitHub token")
            .build()
    )
    options.addOption(
        Option.builder("repo")
            .required()
            .hasArg()
            .desc("GitHub repository")
            .build()
    )
    options.addOption(
        Option.builder("private")
            .hasArg()
            .desc("Ed25519 Private Key")
            .build()
    )

    val cmd = DefaultParser().parse(options, args)

    val token = cmd.getOptionValue("token")
    val plugins = cmd.getOptionValue("plugins")
    val repo = cmd.getOptionValue("repo")
    val privateKeyBase64 = cmd.getOptionValue("private")
    val pluginsDirectory = File(FilenameUtils.normalize(FileUtils.getFile(plugins).absolutePath))

    val gh = GitHubBuilder()
        .withOAuthToken(token)
        .build()

    val okHttpClient = OkHttpClient.Builder().addInterceptor(object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            if (chain.request().header("Authorization") == null) {
                val builder = chain.request().newBuilder()
                builder.header("Authorization", "Bearer $token")
                return chain.proceed(builder.build())
            }
            return chain.proceed(chain.request())
        }
    }).build()


    val date = Date()
    val tagName = DateFormatUtils.format(date, "yyyyMMddHHmmss")
    log.info("Plugins directory: ${pluginsDirectory.absolutePath}")
    log.info("Repo is {}", repo)
    log.info("Tag name: {}", tagName)

    val privateKey = if (privateKeyBase64.isNullOrBlank().not())
        Ed25519.generatePrivate(Base64.decodeBase64(privateKeyBase64)) else null

    return MarketplaceConfig(
        pluginsDirectory = pluginsDirectory,
        gh = gh,
        repo = gh.getRepository(repo),
        okHttpClient = okHttpClient,
        releaseName = DateFormatUtils.format(date, "yyyy-MM-dd HH:mm:ss"),
        tagName = tagName,
        privateKey = privateKey,
    )
}