package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WallpaperProviderService : Service() {

    companion object {
        private const val TAG = "AnimeTVWallpaper"

        private const val SUBREDDIT = "Animewallpaper"

        // Your TV is 1280x720.
        private const val MIN_WIDTH = 1280
        private const val MIN_HEIGHT = 720

        // Accept normal TV/widescreen wallpapers.
        private const val MIN_RATIO = 1.60
        private const val MAX_RATIO = 1.90

        private const val MAX_WALLPAPERS = 15
        private const val RSS_LIMIT = 100

        private val BLOCKED_TERMS = listOf(
            "nsfw",
            "r18",
            "r-18",
            "18+",
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
            "sex",
            "porn",
            "xxx",
            "onlyfans",
            "uncensored"
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
                // No user preferences.
            }
        }

    private fun fetchWallpapers(): List<Wallpaper> {

        val candidates = mutableListOf<String>()

        try {
            val rssUrl =
                "https://www.reddit.com/r/$SUBREDDIT/.rss?limit=$RSS_LIMIT"

            val xml = downloadText(rssUrl)

            if (xml.isBlank()) {
                Log.e(TAG, "Reddit RSS returned empty response")
                return emptyList()
            }

            val parserFactory = XmlPullParserFactory.newInstance()
            parserFactory.isNamespaceAware = false

            val parser = parserFactory.newPullParser()
            parser.setInput(xml.reader())

            var eventType = parser.eventType

            var title = ""
            var description = ""
            var link = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {

                if (eventType == XmlPullParser.START_TAG) {

                    when (parser.name.lowercase(Locale.US)) {

                        "entry", "item" -> {
                            title = ""
                            description = ""
                            link = ""
                        }

                        "title" -> {
                            title = parser.nextText()
                        }

                        "description" -> {
                            description = parser.nextText()
                        }

                        "link" -> {
                            val href = parser.getAttributeValue(null, "href")

                            if (!href.isNullOrBlank()) {
                                link = href
                            } else {
                                link = parser.nextText()
                            }
                        }
                    }
                }

                if (
                    eventType == XmlPullParser.END_TAG &&
                    (parser.name.equals("entry", true) ||
                     parser.name.equals("item", true))
                ) {

                    if (!isBlockedTitle(title)) {

                        val imageUrls =
                            extractImageUrls(description, link)

                        for (imageUrl in imageUrls) {

                            if (candidates.contains(imageUrl)) {
                                continue
                            }

                            if (!isSupportedImage(imageUrl)) {
                                continue
                            }

                            if (isTvWallpaper(imageUrl)) {
                                candidates.add(imageUrl)
                            }

                            if (candidates.size >= MAX_WALLPAPERS) {
                                break
                            }
                        }
                    }
                }

                if (candidates.size >= MAX_WALLPAPERS) {
                    break
                }

                eventType = parser.next()
            }

        } catch (e: Exception) {
            Log.e(TAG, "AnimeWallpaper RSS fetch failed", e)
        }

        candidates.shuffle()

        return candidates.map { url ->
            Wallpaper(
                url,
                WallpaperType.IMAGE,
                author = "r/$SUBREDDIT"
            )
        }
    }

    private fun downloadText(url: String): String {

        val connection =
            (URL(url).openConnection() as HttpURLConnection).apply {

                requestMethod = "GET"

                connectTimeout = 10000
                readTimeout = 15000

                setRequestProperty(
                    "User-Agent",
                    "AnimeTVWallpaperProvider/1.0"
                )

                setRequestProperty(
                    "Accept",
                    "application/rss+xml, application/xml, text/xml"
                )
            }

        return try {

            if (connection.responseCode !in 200..299) {
                Log.e(
                    TAG,
                    "RSS HTTP error: ${connection.responseCode}"
                )
                return ""
            }

            connection.inputStream
                .bufferedReader()
                .use { it.readText() }

        } finally {
            connection.disconnect()
        }
    }

    private fun extractImageUrls(
        description: String,
        link: String
    ): List<String> {

        val result = mutableListOf<String>()

        /*
         * Reddit RSS descriptions commonly contain the preview image.
         * Look for i.redd.it first because those are direct image files.
         */

        val regex =
            Regex(
                """https://(?:i|preview)\.redd\.it/[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]+"""
            )

        regex.findAll(description).forEach { match ->

            var url = match.value

            // HTML escaping.
            url = url
                .replace("&amp;", "&")
                .replace("&quot;", "\"")

            if (!result.contains(url)) {
                result.add(url)
            }
        }

        /*
         * Some RSS responses put the direct image in the entry link.
         */

        if (isSupportedImage(link)) {

            if (!result.contains(link)) {
                result.add(link)
            }
        }

        return result
    }

    private fun isSupportedImage(url: String): Boolean {

        if (url.isBlank()) {
            return false
        }

        val lower = url.lowercase(Locale.US)

        if (
            !lower.startsWith("https://i.redd.it/") &&
            !lower.startsWith("https://preview.redd.it/")
        ) {
            return false
        }

        if (
            lower.contains(".gif") ||
            lower.contains(".gif?") ||
            lower.contains(".mp4") ||
            lower.contains(".webm")
        ) {
            return false
        }

        return true
    }

    private fun isTvWallpaper(url: String): Boolean {

        return try {

            val connection =
                (URL(url).openConnection() as HttpURLConnection).apply {

                    requestMethod = "GET"

                    connectTimeout = 7000
                    readTimeout = 10000

                    setRequestProperty(
                        "User-Agent",
                        "AnimeTVWallpaperProvider/1.0"
                    )
                }

            try {

                if (connection.responseCode !in 200..299) {
                    return false
                }

                val input =
                    BufferedInputStream(connection.inputStream)

                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

                BitmapFactory.decodeStream(
                    input,
                    null,
                    options
                )

                input.close()

                val width = options.outWidth
                val height = options.outHeight

                if (width <= 0 || height <= 0) {
                    return false
                }

                if (width < MIN_WIDTH || height < MIN_HEIGHT) {
                    return false
                }

                val ratio =
                    width.toDouble() / height.toDouble()

                ratio in MIN_RATIO..MAX_RATIO

            } finally {
                connection.disconnect()
            }

        } catch (e: Exception) {
            Log.w(
                TAG,
                "Could not inspect image: $url",
                e
            )

            false
        }
    }

    private fun isBlockedTitle(title: String): Boolean {

        val lower =
            title.lowercase(Locale.US)

        return BLOCKED_TERMS.any { term ->
            lower.contains(term)
        }
    }
}
