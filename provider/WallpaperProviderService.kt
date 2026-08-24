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
            "nsfw", "r18", "r-18", "18+", "ecchi", "hentai", "lewd",
            "nude", "nudity", "boobs", "breast", "bikini", "lingerie",
            "panties", "underboob", "cleavage", "oppai", "fanservice",
            "sexy", "sex", "porn", "xxx", "onlyfans", "uncensored"
        )
    }

    private val binder = object : IWallpaperProviderService.Stub() {
        override fun getWallpapers(event: Event?): List<Wallpaper> = fetchWallpapers()
        override fun getPreferences(): String = "{}"
        override fun setPreferences(params: String) {}
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun fetchWallpapers(): List<Wallpaper> {
        val result = LinkedHashSet<String>()
        try {
            val json = downloadText(
                "https://www.reddit.com/r/$SUBREDDIT/hot.json?limit=$LIMIT&raw_json=1"
            )
            if (json.isBlank()) return emptyList()

            val children = JSONObject(json)
                .getJSONObject("data")
                .getJSONArray("children")

            for (i in 0 until children.length()) {
                if (result.size >= MAX_WALLPAPERS) break

                val post = children.getJSONObject(i).getJSONObject("data")
                val title = post.optString("title", "")
                if (isBlockedTitle(title)) continue

                val previewImages = post.optJSONObject("preview")?.optJSONArray("images")
                val source = previewImages?.optJSONObject(0)?.optJSONObject("source")
                    ?: continue

                val width = source.optInt("width", 0)
                val height = source.optInt("height", 0)
                if (!isTvWallpaper(width, height)) continue

                val direct = post.optString("url_overridden_by_dest", "")
                if (isDirectImage(direct)) {
                    result.add(direct.replace("\\/", "/"))
                } else {
                    val previewUrl = source.optString("url", "")
                    if (isDirectImage(previewUrl)) {
                        result.add(previewUrl.replace("&amp;", "&").replace("\\/", "/"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Animewallpaper", e)
        }

        val shuffled = result.toMutableList()
        shuffled.shuffle()
        return shuffled.take(MAX_WALLPAPERS).map {
            Wallpaper(it, WallpaperType.IMAGE, author = "r/$SUBREDDIT")
        }
    }

    private fun isDirectImage(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase(Locale.US)
        if (!lower.startsWith("https://i.redd.it/") &&
            !lower.startsWith("https://preview.redd.it/")) return false
        return !lower.contains(".gif") && !lower.contains(".mp4") && !lower.contains(".webm")
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
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) {
                Log.e(TAG, "Reddit HTTP error: ${connection.responseCode}")
                return ""
            }
            BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
