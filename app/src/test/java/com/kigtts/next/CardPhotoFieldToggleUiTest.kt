package com.kigtts.next

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.kigtts.next.cards.*
import com.kigtts.next.media.MediaRepository
import com.kigtts.next.theme.*
import com.kigtts.next.ui.cards.CardPhotoFieldTools
import com.kigtts.next.ui.components.MaterialSymbols
import com.kigtts.plugin.api.*
import com.kigtts.plugin.runtime.PluginPaths
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED) @Config(sdk = [35], qualifiers = "zh-rCN-w480dp-h900dp-xhdpi")
class CardPhotoFieldToggleUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun eachFactIsARealToggleAndRepeatedOffOnNeverCreatesAnotherInformationLayer() {
        val app = ApplicationProvider.getApplicationContext<Application>(); MaterialSymbols.initialize(app)
        val paths = PluginPaths(app); val stickers = StickerRepository(app, paths, MediaRepository(app, paths))
        val cards = CardRepository(app, stickers)
        var draft by mutableStateOf(PolaroidCard(title = "", description = "备注"))
        cards.designs.freeze(draft.id, FrozenPhotoFacts(1735689600000L, "UTC"))
        compose.setContent { KigttsTheme(ThemeSettings()) { Box(Modifier.width(420.dp)) {
            CardPhotoFieldTools(cards, draft, true, { layer -> draft = draft.copy(layers = draft.layers.filterNot { it.id == layer.id } + layer) },
                { id -> draft = draft.copy(layers = draft.layers.filterNot { it.id == id }) }, { fail(it) })
        } } }
        compose.onNodeWithTag("card-capture-fields").performClick()
        val date = compose.onNodeWithTag("photo-field/DATE")
        date.assertIsOff().performClick()
        compose.waitUntil(5000) { draft.layers.size == 1 }
        date.assertIsOn().performClick()
        compose.waitForIdle(); date.assertIsOff()
        assertTrue(draft.layers.isEmpty())
        date.performClick()
        compose.waitUntil(5000) { draft.layers.size == 1 }
        date.assertIsOn()
        assertEquals(PhotoTextField.DATE, draft.layers.single().photoField())
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }
}
