package com.kigtts.next.identity

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kigtts.next.testAppModel
import com.kigtts.next.theme.*
import com.kigtts.next.ui.identity.PersonalInformationDialog
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
class AvatarCropUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun image(width: Int = 480, height: Int = 240) = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        eraseColor(Color.RED); Canvas(this).drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.BLUE })
    }
    @Test fun pickingAnAvatarRequiresCropConfirmationBeforeItIsAdded() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("avatar-test", model)
        val input = File(compose.activity.cacheDir, "picked-avatar.png")
        image().let { b -> input.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle() }
        val before = model.stickers.images.value.size; var saved: IdentityCard? = null
        compose.setContent { KigttsTheme(ThemeSettings()) {
            PersonalInformationDialog(IdentityCard(designId = "avatar-test"), model.stickers, {}, { saved = it })
        } }
        compose.onNodeWithText("更换头像").performClick()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(input))) }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("使用这张头像").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("avatar-crop-save") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { assertEquals(before, model.stickers.images.value.size); assertNull(saved) }
        compose.onNodeWithTag("avatar-crop-canvas").performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("使用这张头像").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("裁剪头像").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            val bitmap = BitmapFactory.decodeFile(model.stickers.imageFile(requireNotNull(saved?.avatar))!!.path)
            assertEquals(bitmap.width, bitmap.height); assertEquals(before + 1, model.stickers.images.value.size); bitmap.recycle()
            val original = BitmapFactory.decodeFile(input.path); assertEquals(480, original.width); assertEquals(240, original.height); original.recycle()
        }
    }
    @Test fun cancelingCropKeepsThePreviousAvatarAndDoesNotImportTheSource() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("avatar-cancel-test", model)
        val input = File(compose.activity.cacheDir, "cancel-avatar.png")
        image().let { b -> input.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; b.recycle() }
        val before = model.stickers.images.value.size; var saved: IdentityCard? = null
        compose.setContent { KigttsTheme(ThemeSettings()) { PersonalInformationDialog(IdentityCard(designId = "avatar-test", avatar = "old-avatar"), model.stickers, {}, { saved = it }) } }
        compose.onNodeWithText("更换头像").performClick()
        val request = shadowOf(compose.activity).nextStartedActivityForResult
        compose.runOnIdle { compose.activity.activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(input))) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("裁剪头像").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("取消裁剪").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("old-avatar", saved?.avatar); assertEquals(before, model.stickers.images.value.size) }
    }
    @Test fun exportedAvatarUsesChosenSquareAndBoundsResolutionWithoutChangingTheSource() {
        val model = testAppModel(compose.activity.application)
        compose.activity.viewModelStore.put("avatar-image-test", model)
        val source = image(2400, 1200)
        val id = saveAvatarCrop(model.stickers, source, BackgroundCrop(1f, .5f))
        val bitmap = BitmapFactory.decodeFile(model.stickers.imageFile(id)!!.path)
        try {
            assertEquals(1024, bitmap.width); assertEquals(1024, bitmap.height)
            assertEquals(Color.BLUE, bitmap.getPixel(512, 512)); assertEquals(255, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(Color.RED, source.getPixel(0, 0))
        } finally { source.recycle(); bitmap.recycle() }
    }
}
