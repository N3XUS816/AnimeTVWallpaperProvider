package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WallpaperProviderService : Service() {

    companion object {
        private const val TAG = "AnimeTVWallpaper"

        private const val SUBREDDIT = "Animewallpaper"
        private const val LIMIT = 50
        private const val MAX_WALLPAPERS = 15

        private const val MIN_WIDTH = 1280
        private const val MIN_HEIGHT = 720
        private const val MIN_RATIO = 1.60
        private const val MAX_RATIO = 1.90

        private val BLOCKED_TERMS = listOf(
            "nsfw", "r18", "r-18", "18+", "ecchi", "hentai",
            "lewd", "nude", "nudity", "boobs", "breast", "bikini",
            "lingerie", "panties", "underboob", "cleavage", "oppai",
            "fanservice", "sexy", "sex", "porn", "xxx", "onlyfans",
            "uncensored"
        )
    }

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {
            return fetchWallpapers()
        }

        override fun getPreferences(): String {
            return "{}"
        }

        override fun setPreferences(params: String) {
            // No preferences yet.
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun fetchWallpapers(): List<Wallpaper> {
        val candidates = LinkedHashSet<String>()

        try {
            val url = "https://www.reddit.com/r/$SUBREDDIT/hot.json?limit=$LIMIT&raw_json=1"
            val json = downloadText(url)

            if (json.isBlank()) {
                Log.e(TAG, "Reddit returned an empty response")
                return emptyList()
            }

            val root = JSONObject(json)
            val children = root
                .getJSONObject("data")
                .getJSONArray("children")

            for (i in 0 until children.length()) {
                if (candidates.size >= MAX_WALLPAPERS) break

                val post = children
                    .getJSONObject(i)
                    .getJSONObject("data")

                val title = post.optString("title", "")
                if (isBlockedTitle(title)) continue

                // Prefer Reddit's original destination URL.
                val directUrl = post.optString("url_overridden_by_dest", "")
                addIfValid(candidates, directUrl, null, null)

                // Fall back to Reddit preview metadata when available.
                if (candidates.size < MAX_WALLPAPERS && post.has("preview")) {
                    val preview = post.optJSONObject("preview")
                    val images = preview?.optJSONArray("images")

                    if (images != null && images.length() > 0) {
                        val image = images.optJSONObject(0)
                        val source = image?.optJSONObject("source")

                        if (source != null) {
                            val previewUrl = source.optString("url", "")
                            val width = source.optInt("width", 0)
                            val height = source.optInt("height", 0)

                            addIfValid(
                                candidates,
                                previewUrl,
                                width,
                                height
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Animewallpaper", e)
        }

        val shuffled = candidates.toMutableList()
        shuffled.shuffle()

        return shuffled.take(MAX_WALLPAPERS).map { url ->
            Wallpaper(
                url,
                WallpaperType.IMAGE,
                author = "r/$SUBREDDIT"
            )
        }
    }

    private fun addIfValid(
        candidates: MutableSet<String>,
        rawUrl: String,
        knownWidth: Int?,
        knownHeight: Int?
    ) {
        val url = rawUrl
            .replace("&amp;", "&")
            .replace("\\/", "/")
            .trim()

        if (!isSupportedImage(url)) return

        if (knownWidth != null && knownHeight != null) {
            if (!isTvWallpaper(knownWidth, knownHeight)) return
        }

        candidates.add(url)
    }

    private fun isSupportedImage(url: String): Boolean {
        if (url.isBlank()) return false

        val lower = url.lowercase(Locale.US)

        if (
            !lower.startsWith("https://i.redd.it/") &&
            !lower.startsWith("https://preview.redd.it/")
        ) {
            return false
        }

        if (
            lower.contains(".gif") ||
            lower.contains(".mp4") ||
            lower.contains(".webm")
        ) {
            return false
        }

        return true
    }

    private fun isTvWallpaper(width: Int, height: Int): Boolean {
        if (width < MIN_WIDTH || height < MIN_HEIGHT) return false

        val ratio = width.toDouble() / height.toDouble()
        return ratio in MIN_RATIO..MAX_RATIO
    }

    private fun isBlockedTitle(title: String): Boolean {
        val lower = title.lowercase(Locale.US)
        return BLOCKED_TERMS.any { lower.contains(it) }
    }

    private fun downloadText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty(
                "User-Agent",
                "AnimeWallpaper/1.0 (Projectivy Android TV wallpaper provider)"
            )
            setRequestProperty(
                "Accept",
                "application/json"
            )
        }

        return try {
            if (connection.responseCode !in 200..299) {
                Log.e(TAG, "Reddit HTTP error: ${connection.responseCode}")
                return ""
            }

            BufferedReader(
                InputStreamReader(connection.inputStream)
            ).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
