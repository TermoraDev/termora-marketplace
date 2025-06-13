package app.termora.marketplace

import okhttp3.OkHttpClient
import org.kohsuke.github.GHRepository
import org.kohsuke.github.GitHub
import java.io.File
import java.security.PrivateKey

data class MarketplaceConfig(
    val pluginsDirectory: File,
    val gh: GitHub,
    val repo: GHRepository,
    val okHttpClient: OkHttpClient,
    val releaseName: String,
    val tagName: String,

    /**
     * 对文件进行签名 [Ed25519] ，为空则不签名
     */
    val privateKey: PrivateKey?,
)