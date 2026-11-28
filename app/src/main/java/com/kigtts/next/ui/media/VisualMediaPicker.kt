package com.kigtts.next.ui.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

/** The fallback picker advertised by Play services can be unusable on older Huawei devices. */
internal class VisualMediaPicker : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent {
        if (Build.VERSION.SDK_INT >= 33) return PickVisualMedia().createIntent(context, input)
        val types = when (val media = input.mediaType) {
            PickVisualMedia.ImageOnly -> arrayOf("image/*")
            PickVisualMedia.VideoOnly -> arrayOf("video/*")
            PickVisualMedia.ImageAndVideo -> arrayOf("image/*", "video/*")
            is PickVisualMedia.SingleMimeType -> arrayOf(media.mimeType)
        }
        return OpenDocument().createIntent(context, types).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            if (types.size == 1) type = types.single()
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return intent.data ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }
}
