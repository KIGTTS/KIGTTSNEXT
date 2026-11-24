package com.kigtts.next

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.kigtts.next.ui.onboarding.NativeStartup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w393dp-h852dp-mdpi")
class NativeStartupTest {
    @Test fun legacyCoverUsesTheAppPaletteAndFullWordmarkAtHandoff() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.setContentView(FrameLayout(activity))
            val startup = NativeStartup(activity)
            val colors = lightColorScheme(
                background = Color(0xFFF0ECD8),
                onSurfaceVariant = Color(0xFF4C514B),
                primary = Color(0xFF986500),
            )

            startup.install()
            startup.ready(colors)

            val content = activity.findViewById<FrameLayout>(android.R.id.content)
            val cover = content.getChildAt(1) as FrameLayout
            val mark = cover.getChildAt(0) as ImageView
            val markLayout = mark.layoutParams as FrameLayout.LayoutParams
            assertEquals(colors.background.toArgb(), (cover.background as ColorDrawable).color)
            assertEquals(R.drawable.logo_horizontal, shadowOf(mark.drawable).createdFromResId)
            assertEquals(Gravity.CENTER, markLayout.gravity)
            assertEquals(180, markLayout.width)
            assertEquals(45, markLayout.height)
            assertEquals(lerp(colors.onSurfaceVariant, colors.primary, 0.16f).toArgb(), mark.imageTintList?.defaultColor)
            assertNotEquals(colors.primary.toArgb(), mark.imageTintList?.defaultColor)
        }
    }

    @Test
    @Config(sdk = [35])
    fun platformIconKeepsTheHorizontalMarkWithinTheNativeMask() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val mark = controller.get().getDrawable(R.drawable.startup_mark) as InsetDrawable
            mark.setBounds(0, 0, 288, 288)

            val bounds = requireNotNull(mark.drawable).bounds
            assertEquals(180, bounds.width())
            assertEquals(45f, bounds.height().toFloat(), 1f)
            assertEquals(144, bounds.centerX())
            assertEquals(144, bounds.centerY())
        }
    }
}
