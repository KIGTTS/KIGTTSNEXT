package com.kigtts.next.sharing

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.testAppModel
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext

@RunWith(RobolectricTestRunner::class) @Config(sdk = [35])
class FaceReceivingTest {
    @Test fun closingShareBeforeHotspotStartupDoesNotReopenTheSession() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val queued = ArrayDeque<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { queued.add(block) }
        }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val model = testAppModel(context)
        val controller = FaceShareController(context, scope, model.cards, model.identities, {})
        try {
            controller.openSend(emptyList())
            controller.startHotspot()
            assertEquals(1, queued.size)
            controller.finishSending()
            queued.removeFirst().run()
            assertTrue("关闭后的热点请求不得重新创建后台会话", scope.coroutineContext[Job]!!.children.none())
            assertFalse(controller.hotspot.starting.value)
            assertNull(controller.hotspot.details.value)
        } finally { controller.stop(); scope.cancel() }
    }
    @Test fun incomingAlertIsHighPriorityAndRemovedOnDecisionOrSessionEnd() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifications = FaceIncomingNotifications(context)
        val offer = FaceOffer(faceToken(), faceToken(), JSONObject().put("nickname", "朋友"),
            listOf(FaceAsset(faceToken(), faceToken(), "polaroid", "photo.jpg", "image/jpeg", 2000, "a".repeat(64), "SQUARE")))
        notifications.update(listOf(offer))
        val notification = shadowOf(manager).allNotifications.single()
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(FaceIncomingNotifications.CHANNEL).importance)
        assertEquals("朋友 想与你分享", notification.extras.getString("android.title"))
        assertEquals(listOf("拒绝", "接收"), notification.actions.map { it.title.toString() })
        notifications.update(listOf(offer))
        assertEquals(1, shadowOf(manager).allNotifications.size)
        notifications.update(emptyList())
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
        notifications.update(listOf(offer.copy(id = faceToken())))
        notifications.clear()
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
    @Test fun receivingIsRememberedWithoutOpeningAModalAndStopExplicitlyDisablesIt() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        // Keep network startup queued: this test covers durable intent and panel ownership.
        val dispatcher = object : CoroutineDispatcher() { override fun dispatch(context: CoroutineContext, block: Runnable) = Unit }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val model = testAppModel(context)
        fun controller() = FaceShareController(context, scope, model.cards, model.identities, {})
        val first = controller()
        try {
            assertFalse(first.receiving.value)
            first.setReceiving(true)
            assertTrue(first.receiving.value); assertNull(first.panel.value)
            first.dispose()
            val restored = controller()
            assertTrue(restored.receiving.value)
            restored.openSend(emptyList()); restored.hide()
            assertTrue(restored.receiving.value); assertNull(restored.panel.value)
            restored.stop(); assertFalse(restored.receiving.value)
            val next = controller(); assertFalse(next.receiving.value); next.dispose()
        } finally { first.stop(); scope.cancel() }
    }
    @Test fun receiveEntryAndToggleNeverReplaceOrCloseAnOutgoingSelection() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val dispatcher = object : CoroutineDispatcher() { override fun dispatch(context: CoroutineContext, block: Runnable) = Unit }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val model = testAppModel(context)
        val controller = FaceShareController(context, scope, model.cards, model.identities, {})
        try {
            controller.openSend(listOf("first-photo", "second-photo"))
            val original = controller.panel.value
            assertEquals(false, original?.personal)
            controller.openReceive()
            assertTrue(controller.receiving.value); assertSame(original, controller.panel.value)
            controller.setReceiving(false)
            assertFalse(controller.receiving.value); assertSame(original, controller.panel.value)
            assertEquals("", controller.status.value); assertFalse(controller.busy.value)
            controller.setReceiving(true)
            controller.finishSending()
            assertTrue(controller.receiving.value); assertNull(controller.panel.value)
            controller.openSend(listOf("third-photo")); controller.hide()
            assertTrue(controller.receiving.value); assertNull(controller.panel.value)
        } finally { controller.stop(); scope.cancel() }
    }
}
