package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            text = """
                AnimeWallpaper

                Source:
                r/Animewallpaper

                Filters:
                • Landscape / TV wallpapers
                • Minimum 1280×720
                • 16:9-style aspect ratio
                • Direct Reddit images only
                • GIF/video excluded
                • Conservative adult-content title filter

                Projectivy will cache wallpapers and rotate them
                according to your wallpaper settings.
            """.trimIndent()

            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 48, 48, 48)
        }

        setContentView(text)
    }
}
