package com.kigtts.next.sharing

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import com.kigtts.next.MainActivity
import com.kigtts.next.R

/** Only pending offers get alerts; resolving or ending the session removes them. */
internal class FaceIncomingNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val posted = mutableMapOf<String, Int>()
    private var nextId = 4810
    var foreground = true
    fun update(offers: List<FaceOffer>) {
        val pending = offers.filter { it.status == "waiting" }
        posted.keys.filter { id -> pending.none { it.id == id } }.forEach { manager.cancel(posted.remove(it)!!) }
        pending.filterNot { it.id in posted }.forEach { offer ->
            val id = nextId++
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "面对面接收请求", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            fun action(name: String) = PendingIntent.getService(context, id,
                Intent(context, FaceShareService::class.java).setAction(name).putExtra("offer", offer.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_face_share)
                .setContentTitle("${offer.nickname} 想与你分享").setContentText(faceOfferSummary(offer))
                .setContentIntent(open).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setSilent(foreground)
                .setOnlyAlertOnce(true).addAction(0, "拒绝", action("reject")).addAction(0, "接收", action("accept")).build()
            if (runCatching { manager.notify(id, notification) }.isSuccess) posted[offer.id] = id
        }
    }
    fun clear() { posted.values.forEach(manager::cancel); posted.clear() }
    companion object { const val CHANNEL = "face-incoming" }
}

internal fun faceOfferSummary(offer: FaceOffer): String {
    val pictures = offer.assets.filterNot { it.format == "PROFILE" }
    return if (pictures.isNotEmpty() && pictures.all { it.format == "BANNER" }) "名片 · 接收后加入扩列收藏夹"
    else "${pictures.map { it.photo }.distinct().size} 张照片 · %.1f MB".format(offer.assets.sumOf { it.bytes } / 1048576.0)
}
