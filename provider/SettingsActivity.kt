package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {

            text = """
                Anime TV Wallpapers

                Source:
                r/Animewallpaper

                TV filtering:

                • Landscape only
                • 16:9 TV ratio
                • Minimum 1280×720
                • Direct Reddit images only
                • GIF/video excluded
                • Portrait/mobile wallpapers excluded
                • Conservative suggestive-title filter
                • Up to 15 wallpapers per refresh

                Designed for Android TV and Projectivy Launcher.
            """.trimIndent()

            textSize = 18f

            setPadding(
                40,
                40,
                40,
                40
            )
        }

        setContentView(text)
    }
}
