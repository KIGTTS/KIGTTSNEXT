package com.kigtts.next.ui.media

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.GetContent
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

internal val PickGalleryImage = PickVisualMediaRequest(PickVisualMedia.ImageOnly)

/** Pick local media on older devices without requesting the Play services backport. */
internal class VisualMediaPicker : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent {
        if (Build.VERSION.SDK_INT >= 33) return PickVisualMedia().createIntent(context, input)
        val types = when (val media = input.mediaType) {
            PickVisualMedia.ImageOnly -> listOf("image/*")
            PickVisualMedia.VideoOnly -> listOf("video/*")
            PickVisualMedia.ImageAndVideo -> listOf("image/*", "video/*")
            is PickVisualMedia.SingleMimeType -> listOf(media.mimeType)
        }
        val type = types.singleOrNull()
        return type?.let { systemGallery(context, it) } ?: GetContent().createIntent(context, type ?: "*/*").apply {
            if (type == null) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return intent.data ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private fun systemGallery(context: Context, mimeType: String): Intent? {
        val picker = Intent(Intent.ACTION_PICK).setType(mimeType)
        val gallery = context.packageManager.queryIntentActivities(picker, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo }
            .firstOrNull {
                it.exported && it.packageName != context.packageName && it.packageName != "com.google.android.gms" &&
                    it.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            } ?: return null
        return picker.setComponent(ComponentName(gallery.packageName, gallery.name))
    }
}
