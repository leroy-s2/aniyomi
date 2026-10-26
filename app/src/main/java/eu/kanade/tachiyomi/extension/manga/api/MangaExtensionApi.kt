package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import logcat.LogPriority
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import okio.BufferedSource
import okio.buffer
import okio.gzip
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days

internal class MangaExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetMangaExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateMangaExtensionRepo by injectLazy()
    private val extensionManager: MangaExtensionManager by injectLazy()
    private val json: Json by injectLazy()
    private val protoBuf = ProtoBuf { }

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<MangaExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl
        return try {
            val response = try {
                networkService.client.newCall(GET("$repoBaseUrl/index.pb")).awaitSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                networkService.client.newCall(GET("$repoBaseUrl/index.min.json")).awaitSuccess()
            }

            response.body.source().decompressIfGzipped().use { source ->
                val firstByte = source.peek().use { it.readByte() }
                if (firstByte == '{'.code.toByte() || firstByte == '['.code.toByte()) {
                    json.decodeFromBufferedSource<List<ExtensionJsonObject>>(source)
                        .toExtensions(repoBaseUrl)
                } else {
                    protoBuf.decodeFromByteArray<NetworkExtensionStore>(source.readByteArray())
                        .extensionList
                        .orEmpty()
                        .toExtensions(repoBaseUrl)
                }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<MangaExtension.Installed>? {
        // Limit checks to once a day at most
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        // Update extension repo details
        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = MangaExtensionLoader.loadMangaExtensions(context)
            .filterIsInstance<MangaLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }

        return extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<MangaExtension.Available> {
        return this
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion >= MangaExtensionLoader.LIB_VERSION_MIN && libVersion <= MangaExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion(),
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    fun getApkUrl(extension: MangaExtension.Available): String {
        return extension.apkName.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: "${extension.repoUrl}/apk/${extension.apkName}"
    }

    @Serializable
    private data class NetworkExtensionStore(
        @ProtoNumber(101) val extensionList: ExtensionList? = null,
    ) {
        @Serializable
        data class ExtensionList(
            @ProtoNumber(1) val extensions: List<Extension>,
        )

        @Serializable
        data class Extension(
            @ProtoNumber(1) val name: String,
            @ProtoNumber(2) val packageName: String,
            @ProtoNumber(3) val resources: Resources,
            @ProtoNumber(4) val extensionLib: String,
            @ProtoNumber(5) val versionCode: Long,
            @ProtoNumber(6) val versionName: String,
            @ProtoNumber(7) val contentWarning: ContentWarning,
            @ProtoNumber(9) val sources: List<Source>,
        )

        @Serializable
        data class Resources(
            @ProtoNumber(1) val apkUrl: String,
            @ProtoNumber(2) val iconUrl: String,
        )

        @Serializable
        data class Source(
            @ProtoNumber(1) val id: Long,
            @ProtoNumber(2) val name: String,
            @ProtoNumber(3) val language: String,
            @ProtoNumber(4) val homeUrl: String = "",
        )

        @Serializable
        enum class ContentWarning {
            @ProtoNumber(0)
            UNSPECIFIED,

            @ProtoNumber(1)
            SAFE,

            @ProtoNumber(2)
            MIXED,

            @ProtoNumber(3)
            NSFW,
        }
    }

    private fun List<NetworkExtensionStore.Extension>.toExtensions(
        repoUrl: String,
    ): List<MangaExtension.Available> {
        return map { extension ->
            MangaExtension.Available(
                name = extension.name,
                pkgName = extension.packageName,
                versionName = extension.versionName,
                versionCode = extension.versionCode,
                libVersion = extension.extensionLib.toDouble(),
                lang = extension.sources.map { it.language }.distinct().singleOrNull() ?: "all",
                isNsfw = extension.contentWarning >= NetworkExtensionStore.ContentWarning.MIXED,
                sources = extension.sources.map {
                    MangaExtension.Available.MangaSource(
                        id = it.id,
                        lang = it.language,
                        name = it.name,
                        baseUrl = it.homeUrl,
                    )
                },
                apkName = extension.resources.apkUrl,
                iconUrl = extension.resources.iconUrl,
                repoUrl = repoUrl,
            )
        }
    }

    private fun BufferedSource.decompressIfGzipped(): BufferedSource {
        val isGzip = peek().use { peeked ->
            try {
                peeked.readShort().toInt() == 0x1f8b
            } catch (_: Exception) {
                false
            }
        }
        return if (isGzip) gzip().buffer() else this
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double {
        return version.substringBeforeLast('.').toDouble()
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private val extensionSourceMapper: (ExtensionSourceJsonObject) -> MangaExtension.Available.MangaSource = {
    MangaExtension.Available.MangaSource(
        id = it.id,
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
    )
}
