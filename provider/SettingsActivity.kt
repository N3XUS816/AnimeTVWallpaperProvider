package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            text = """
                Anime TV Wallpaper Provider

                Source: r/Animewallpaper

                Filters:
                • Landscape TV wallpapers
                • Minimum 1280×720
                • SFW Reddit posts only
                • Image posts only
                • No GIF/video posts
                • Conservative suggestive-title filter
            """.trimIndent()

            textSize = 18f
            setPadding(40, 40, 40, 40)
        }

        setContentView(text)
    }
}
