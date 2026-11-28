package com.kigtts.next.desktop

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import com.kigtts.next.MainActivity
import com.kigtts.next.R
import com.kigtts.next.state.AppViewModel
import com.kigtts.next.state.Destination
import com.kigtts.next.state.SidePage

private const val ACTION_PREFIX = "com.lhtstudio.kigtts.app.desktop."

internal enum class DesktopEntry(val id: String, val label: String, @param:DrawableRes val icon: Int) {
    HOME("home", "app.name", R.drawable.ic_launcher_monochrome),
    CAPTIONS("captions", "desktop.captions", R.drawable.ic_desktop_captions),
    DRAWING("drawing", "quick.sketch", R.drawable.ic_desktop_drawing),
    CAMERA("camera", "nav.camera", R.drawable.ic_desktop_camera),
    PROFILE("profile", "nav.profile", R.drawable.ic_desktop_profile),
    PROFILE_QR("profile-qr", "profile.qr", R.drawable.ic_desktop_qr),
    PROFILE_SHARE("profile-share", "profile.share", R.drawable.ic_desktop_share);

    fun intent(context: Context) = Intent(context, MainActivity::class.java).apply {
        action = ACTION_PREFIX + id
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    fun open(model: AppViewModel) {
        model.closeSidePage()
        when (this) {
            HOME -> model.navigate(Destination.Home)
            CAPTIONS -> {
                model.closePanel()
                model.navigate(Destination.Home)
            }
            DRAWING -> model.openSidePage(SidePage.SKETCH)
            CAMERA -> model.cardCapture.open(systemCamera = false)
            PROFILE, PROFILE_QR, PROFILE_SHARE -> {
                model.desktopProfileAction.value = takeUnless { it == PROFILE }
                model.navigate(Destination.PersonalCards)
            }
        }
    }

    companion object {
        val quick = listOf(CAPTIONS, DRAWING, CAMERA)
        fun from(intent: Intent?) = entries.firstOrNull { intent?.action == ACTION_PREFIX + it.id }
    }
}
