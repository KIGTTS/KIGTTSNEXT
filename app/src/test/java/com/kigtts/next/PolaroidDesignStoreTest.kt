package com.kigtts.next

import com.kigtts.next.cards.*
import com.kigtts.plugin.api.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PolaroidDesignStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun defaultCopiesDesignAndResolvesFrozenFieldsWithoutReplacingNewOriginalOrCrop() {
        val store = PolaroidDesignStore(folder.root)
        val facts = FrozenPhotoFacts(1704067200000L, "UTC", location = "拍摄地点")
        val date = CardLayer(kind = CardLayerKind.TEXT, text = facts.text(PhotoTextField.DATE), width = .04)
        val sticker = CardLayer(kind = CardLayerKind.IMAGE, imageId = "sticker", rotation = 23.0, flipX = true)
        val source = PolaroidCard(title = "旧图", imageId = "old", layers = listOf(date, sticker), frameStyleId = "instax.mini.night")
        store.bind(source.id, date, PhotoTextField.DATE)
        val preset = store.savePreset("我的框", source, true)
        val target = PolaroidCard(title = "", imageId = "new", originalImageId = "full-resolution", photoX = .2, photoZoom = 2.0)
        store.freeze(target.id, facts.copy(capturedAt = 1735689600000L))
        val applied = store.apply(target)
        assertEquals("new", applied.imageId); assertEquals("full-resolution", applied.originalImageId)
        assertEquals(.2, applied.photoX, 0.0); assertEquals(2.0, applied.photoZoom, 0.0)
        assertEquals("2025.01.01", applied.layers[0].text)
        assertEquals(CardLayerKind.PHOTO_INFO, applied.layers[0].kind)
        assertEquals(CardLayerKind.PHOTO_INFO, store.normalizeFields(source).layers[0].kind)
        assertEquals(CardLayerKind.TEXT, store.normalizeFields(source.copy(layers = listOf(date.copy(text = "自定义")))).layers[0].kind)
        assertEquals(23.0, applied.layers[1].rotation, 0.0); assertTrue(applied.layers[1].flipX)
        assertTrue(applied.layers.none { it.id == date.id || it.id == sticker.id })
        val reloaded = PolaroidDesignStore(folder.root)
        assertEquals(preset.id, reloaded.state.value.defaultId)
        assertEquals("2025.01.01", reloaded.facts(target.id).text(PhotoTextField.DATE))
        assertEquals(setOf("sticker"), reloaded.referencedImages())
        reloaded.removePreset(preset.id); assertNull(reloaded.state.value.defaultId); assertTrue(reloaded.referencedImages().isEmpty())
    }
    @Test fun manuallyChangedFieldBecomesStaticAndMissingCaptureTimeIsNeverNow() {
        val store = PolaroidDesignStore(folder.root)
        val date = CardLayer(kind = CardLayerKind.TEXT, text = "2024.01.01")
        val card = PolaroidCard(title = "", layers = listOf(date.copy(text = "手写纪念日")))
        store.bind(card.id, date, PhotoTextField.DATE)
        store.savePreset("静态文字", card, true)
        val applied = store.apply(PolaroidCard(title = ""))
        assertEquals("手写纪念日", applied.layers.single().text)
        assertEquals("拍摄时间未知", store.facts("missing").text(PhotoTextField.DATE))
    }
}
