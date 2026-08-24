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
import java.net.HttpURLConnection
import java.net.URL

class WallpaperProviderService : Service() {

    companion object {
        private const val TAG = "AnimeTVWallpaper"
        private const val SUBREDDIT = "Animewallpaper"

        private const val MIN_WIDTH = 1280
        private const val MIN_HEIGHT = 720
        private const val MIN_RATIO = 1.60
        private const val MAX_RATIO = 1.90

        private const val MAX_WALLPAPERS = 15
        private const val REQUEST_LIMIT = 100

        private val BLOCKED_TERMS = listOf(
            "nsfw",
            "r18",
            "r-18",
            "ecchi",
            "hentai",
            "lewd",
            "nude",
            "nudity",
            "boobs",
            "breast",
            "bikini",
            "lingerie",
            "panties",
            "underboob",
            "cleavage",
            "oppai",
            "fanservice",
            "sexy",
            "onlyfans",
            "uncensored",
            "18+"
        )
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private val binder =
        object : IWallpaperProviderService.Stub() {

            override fun getWallpapers(event: Event?): List<Wallpaper> {
                return fetchWallpapers()
            }

            override fun getPreferences(): String {
                return "{}"
            }

            override fun setPreferences(params: String) {
                // No settings yet.
            }
        }

    private fun fetchWallpapers(): List<Wallpaper> {
        return try {
            val urls = mutableListOf<String>()

            val endpoints = listOf(
                "https://www.reddit.com/r/$SUBREDDIT/hot.json?limit=$REQUEST_LIMIT&raw_json=1",
                "https://www.reddit.com/r/$SUBREDDIT/top.json?t=month&limit=$REQUEST_LIMIT&raw_json=1"
            )

            for (endpoint in endpoints) {
                if (urls.size >= MAX_WALLPAPERS) break

                val json = getJson(endpoint) ?: continue

                val posts = json
                    .optJSONObject("data")
                    ?.optJSONArray("children")
                    ?: continue

                for (i in 0 until posts.length()) {
                    if (urls.size >= MAX_WALLPAPERS) break

                    val post = posts
                        .optJSONObject(i)
                        ?.optJSONObject("data")
                        ?: continue

                    if (post.optBoolean("over_18", false)) continue
                    if (post.optBoolean("is_video", false)) continue

                    val title = post.optString("title", "")

                    if (BLOCKED_TERMS.any {
                            title.contains(it, ignoreCase = true)
                        }) {
                        continue
                    }

                    val previewSource = post
                        .optJSONObject("preview")
                        ?.optJSONArray("images")
                        ?.optJSONObject(0)
                        ?.optJSONObject("source")

                    val width = previewSource?.optInt("width", 0) ?: 0
                    val height = previewSource?.optInt("height", 0) ?: 0

                    if (width < MIN_WIDTH || height < MIN_HEIGHT) {
                        continue
                    }

                    if (height == 0) continue

                    val ratio = width.toDouble() / height.toDouble()

                    if (ratio < MIN_RATIO || ratio > MAX_RATIO) {
                        continue
                    }

                    val imageUrl =
                        post.optString("url_overridden_by_dest")
                            .ifBlank {
                                post.optString("url")
                            }

                    if (!isDirectImage(imageUrl)) continue
                    if (urls.contains(imageUrl)) continue

                    urls.add(imageUrl)
                }
            }

            urls.shuffled().map { url ->
                Wallpaper(
                    url,
                    WallpaperType.IMAGE,
                    author = "r/$SUBREDDIT"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Wallpaper fetch failed", e)
            emptyList()
        }
    }

    private fun getJson(url: String): JSONObject? {

        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {

                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 10000

                setRequestProperty(
                    "User-Agent",
                    "AnimeTVWallpaperProvider/1.0"
                )

                setRequestProperty(
                    "Accept",
                    "application/json"
                )
            }

        return try {

            if (connection.responseCode !in 200..299) {
                return null
            }

            connection.inputStream
                .bufferedReader()
                .use {
                    JSONObject(it.readText())
                }

        } finally {
            connection.disconnect()
        }
    }

    private fun isDirectImage(url: String): Boolean {

        if (url.isBlank()) return false

        val lower = url.lowercase()

        return (
            lower.startsWith("https://i.redd.it/") ||
            lower.startsWith("https://preview.redd.it/")
        ) &&
                !lower.contains(".gif") &&
                !lower.contains(".mp4") &&
                !lower.contains(".webm")
    }
}
